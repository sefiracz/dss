/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.validation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.jaxb.XmlDiagnosticData;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.TimestampedObjectType;
import eu.europa.esig.dss.enumerations.TokenExtractionStategy;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.policy.EtsiValidationPolicy;
import eu.europa.esig.dss.policy.ValidationPolicy;
import eu.europa.esig.dss.policy.ValidationPolicyFacade;
import eu.europa.esig.dss.policy.jaxb.ConstraintsParameters;
import eu.europa.esig.dss.spi.DSSSecurityProvider;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.ListCertificateSource;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.executor.DocumentProcessExecutor;
import eu.europa.esig.dss.validation.executor.ValidationLevel;
import eu.europa.esig.dss.validation.executor.signature.DefaultSignatureProcessExecutor;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.validation.scope.SignatureScope;
import eu.europa.esig.dss.validation.scope.SignatureScopeFinder;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;
import eu.europa.esig.dss.validation.timestamp.TimestampedReference;

/**
 * Validates a signed document. The content of the document is determined
 * automatically. It can be: XML, CAdES(p7m), PDF or ASiC(zip).
 * SignatureScopeFinder can be set using the appropriate setter (ex.
 * setCadesSignatureScopeFinder). By default, this class will use the default
 * SignatureScopeFinder as defined by
 * eu.europa.esig.dss.validation.scope.SignatureScopeFinderFactory
 */
public abstract class SignedDocumentValidator implements DocumentValidator {

	private static final Logger LOG = LoggerFactory.getLogger(SignedDocumentValidator.class);

	static {
		Security.addProvider(DSSSecurityProvider.getSecurityProvider());
	}

	/**
	 * This variable can hold a specific {@code DocumentProcessExecutor}
	 */
	protected DocumentProcessExecutor processExecutor = null;
	
	/**
	 * The document to be validated (with the signature(s) or timestamp(s))
	 */
	protected DSSDocument document;

	/**
	 * In case of a detached signature this {@code List} contains the signed
	 * documents.
	 */
	protected List<DSSDocument> detachedContents = new ArrayList<>();
	
	/**
	 * In case of an ASiC signature this {@code List} of container documents.
	 */
	protected List<DSSDocument> containerContents;
	
	/**
	 * List of all found {@link ManifestFile}s
	 */
	protected List<ManifestFile> manifestFiles;

	protected CertificateToken providedSigningCertificateToken = null;
	
	/**
	 * A time to validate the document against
	 */
	private Date validationTime;

	/**
	 * The reference to the certificate verifier. The current DSS implementation
	 * proposes {@link eu.europa.esig.dss.validation.CommonCertificateVerifier}.
	 * This verifier encapsulates the references to different sources used in the
	 * signature validation process.
	 */
	protected CertificateVerifier certificateVerifier;

	private TokenExtractionStategy tokenExtractionStategy = TokenExtractionStategy.NONE;

	/**
	 * This variable allows to include the semantics for Indication / SubIndication
	 */
	private boolean includeSemantics = false;

	protected final SignatureScopeFinder signatureScopeFinder;

	private SignaturePolicyProvider signaturePolicyProvider;

	// Default configuration with the highest level
	private ValidationLevel validationLevel = ValidationLevel.ARCHIVAL_DATA;
	
	/**
	 * Locale to use for reports generation
	 * By default a Locale from OS is used
	 */
	private Locale locale = Locale.getDefault();

	// Produces the ETSI Validation Report by default
	private boolean enableEtsiValidationReport = true;

	// Disable certificate chain building, revocation data collection,...
	protected boolean skipValidationContextExecution = false;

	protected SignedDocumentValidator() {
		this.signatureScopeFinder = null;
	}

	protected SignedDocumentValidator(SignatureScopeFinder signatureScopeFinder) {
		this.signatureScopeFinder = signatureScopeFinder;
	}

	protected void setSignedScopeFinderDefaultDigestAlgorithm(DigestAlgorithm digestAlgorithm) {
		// Null in the ASiC Container validator
		if (signatureScopeFinder != null) {
			signatureScopeFinder.setDefaultDigestAlgorithm(digestAlgorithm);
		}
	}

	/**
	 * This method guesses the document format and returns an appropriate
	 * document validator.
	 *
	 * @param dssDocument
	 *            The instance of {@code DSSDocument} to validate
	 * @return returns the specific instance of SignedDocumentValidator in terms
	 *         of the document type
	 */
	public static SignedDocumentValidator fromDocument(final DSSDocument dssDocument) {
		Objects.requireNonNull(dssDocument, "DSSDocument is null");
		ServiceLoader<DocumentValidatorFactory> serviceLoaders = ServiceLoader.load(DocumentValidatorFactory.class);
		for (DocumentValidatorFactory factory : serviceLoaders) {
			if (factory.isSupported(dssDocument)) {
				return factory.create(dssDocument);
			}
		}
		throw new DSSException("Document format not recognized/handled");
	}

	public abstract boolean isSupported(DSSDocument dssDocument);

	@Override
	public void defineSigningCertificate(final CertificateToken token) {
		Objects.requireNonNull(token, "Token is not defined");
		providedSigningCertificateToken = token;
	}

	/**
	 * To carry out the validation process of the signature(s) some external sources
	 * of certificates and of revocation data can be needed. The certificate
	 * verifier is used to pass these values. Note that once this setter is called
	 * any change in the content of the <code>CommonTrustedCertificateSource</code>
	 * or in adjunct certificate source is not taken into account.
	 *
	 * @param certificateVerifier
	 */
	@Override
	public void setCertificateVerifier(final CertificateVerifier certificateVerifier) {
		this.certificateVerifier = certificateVerifier;
	}

	@Override
	public void setTokenExtractionStategy(TokenExtractionStategy tokenExtractionStategy) {
		Objects.requireNonNull(tokenExtractionStategy);
		this.tokenExtractionStategy = tokenExtractionStategy;
	}

	@Override
	public void setIncludeSemantics(boolean include) {
		this.includeSemantics = include;
	}

	@Override
	public void setDetachedContents(final List<DSSDocument> detachedContents) {
		this.detachedContents = detachedContents;
	}

	@Override
	public void setContainerContents(List<DSSDocument> containerContents) {
		this.containerContents = containerContents;
	}
	
	@Override
	public void setManifestFiles(List<ManifestFile> manifestFiles) {
		this.manifestFiles = manifestFiles;
	}

	/**
	 * Returns a default digest algorithm defined for a digest calculation
	 * 
	 * @return {@link DigestAlgorithm}
	 */
	protected DigestAlgorithm getDefaultDigestAlgorithm() {
		return certificateVerifier.getDefaultDigestAlgorithm();
	}

	/**
	 * Allows to define a custom validation time
	 * 
	 * @param validationTime {@link Date}
	 */
	@Override
	public void setValidationTime(Date validationTime) {
		this.validationTime = validationTime;
	}

	/**
	 * Returns validation time In case if the validation time is not provided,
	 * initialize the current time value from the system
	 * 
	 * @return {@link Date} validation time
	 */
	protected Date getValidationTime() {
		if (validationTime == null) {
			validationTime = new Date();
		}
		return validationTime;
	}

	@Override
	public void setValidationLevel(ValidationLevel validationLevel) {
		this.validationLevel = validationLevel;
	}

	@Override
	public void setEnableEtsiValidationReport(boolean enableEtsiValidationReport) {
		this.enableEtsiValidationReport = enableEtsiValidationReport;
	}

	@Override
	public Reports validateDocument() {
		return validateDocument((InputStream) null);
	}

	@Override
	public Reports validateDocument(final URL validationPolicyURL) {
		if (validationPolicyURL == null) {
			return validateDocument((InputStream) null);
		}
		try {
			return validateDocument(validationPolicyURL.openStream());
		} catch (IOException e) {
			throw new DSSException(e);
		}
	}

	@Override
	public Reports validateDocument(final String policyResourcePath) {
		if (policyResourcePath == null) {
			return validateDocument((InputStream) null);
		}
		return validateDocument(getClass().getResourceAsStream(policyResourcePath));
	}

	@Override
	public Reports validateDocument(final File policyFile) {
		if ((policyFile == null) || !policyFile.exists()) {
			return validateDocument((InputStream) null);
		}
		final InputStream inputStream = DSSUtils.toByteArrayInputStream(policyFile);
		return validateDocument(inputStream);
	}

	/**
	 * Validates the document and all its signatures. The policyDataStream contains
	 * the constraint file. If null or empty the default file is used.
	 *
	 * @param policyDataStream the {@code InputStream} with the validation policy
	 * @return the validation reports
	 */
	@Override
	public Reports validateDocument(final InputStream policyDataStream) {
		ValidationPolicy validationPolicy = null;
		try {
			if (policyDataStream == null) {
				LOG.debug("No provided validation policy : use the default policy");
				validationPolicy = ValidationPolicyFacade.newFacade().getDefaultValidationPolicy();
			} else {
				validationPolicy = ValidationPolicyFacade.newFacade().getValidationPolicy(policyDataStream);
			}
		} catch (Exception e) {
			throw new DSSException("Unable to load the policy", e);
		}
		return validateDocument(validationPolicy);
	}

	/**
	 * Validates the document and all its signatures. The
	 * {@code validationPolicyDom} contains the constraint file. If null or empty
	 * the default file is used.
	 *
	 * @param validationPolicyJaxb the {@code ConstraintsParameters} to use in the
	 *                             validation process
	 * @return the validation reports
	 */
	@Override
	public Reports validateDocument(final ConstraintsParameters validationPolicyJaxb) {
		final ValidationPolicy validationPolicy = new EtsiValidationPolicy(validationPolicyJaxb);
		return validateDocument(validationPolicy);
	}

	/**
	 * Validates the document and all its signatures. The
	 * {@code validationPolicyDom} contains the constraint file. If null or empty
	 * the default file is used.
	 *
	 * @param validationPolicy the {@code ValidationPolicy} to use in the validation
	 *                         process
	 * @return the validation reports
	 */
	@Override
	public Reports validateDocument(final ValidationPolicy validationPolicy) {
		LOG.info("Document validation...");
		assertConfigurationValid();

		final ValidationContext validationContext = new SignatureValidationContext();

		final XmlDiagnosticData diagnosticData = prepareDiagnosticDataBuilder(validationContext).build();

		return processValidationPolicy(diagnosticData, validationPolicy);
	}

	/**
	 * Checks if the Validator configuration is valid
	 */
	protected void assertConfigurationValid() {
		Objects.requireNonNull(certificateVerifier, "CertificateVerifier is not defined");
		Objects.requireNonNull(document, "Document is not provided to the validator");
	}

	/**
	 * Creates a DiagnosticData to pass to the validation process
	 * 
	 * @param validationContext {@link ValidationContext}
	 * @return {@link DiagnosticData}
	 */
	protected DiagnosticDataBuilder prepareDiagnosticDataBuilder(final ValidationContext validationContext) {
		
		List<AdvancedSignature> allSignatures = getAllSignatures();
        List<TimestampToken> detachedTimestamps = getDetachedTimestamps();

		ListRevocationSource<CRL> listCRLSource = mergeCRLSources(allSignatures, detachedTimestamps);
		ListRevocationSource<OCSP> listOCSPSource = mergeOCSPSources(allSignatures, detachedTimestamps);
		ListCertificateSource listCertificateSource = mergeCertificateSource(allSignatures, detachedTimestamps);
        
		prepareCertificateVerifier(listCRLSource, listOCSPSource, listCertificateSource);
		
		prepareSignatureValidationContext(validationContext, allSignatures);
        prepareDetachedTimestampValidationContext(validationContext, detachedTimestamps);
		
		if (!skipValidationContextExecution) {
			validateContext(validationContext);
		}
		
		return getDiagnosticDataBuilderConfiguration(validationContext, allSignatures, listCRLSource, listOCSPSource);
	}
	
	protected DiagnosticDataBuilder getDiagnosticDataBuilderConfiguration(final ValidationContext validationContext, List<AdvancedSignature> signatures,
			final ListRevocationSource<CRL> listCRLSource, final ListRevocationSource<OCSP> listOCSPSource) {
		return new DiagnosticDataBuilder().document(document).usedTimestamps(validationContext.getProcessedTimestamps())
				.usedCertificates(validationContext.getProcessedCertificates()).usedRevocations(validationContext.getProcessedRevocations())
				.setDefaultDigestAlgorithm(certificateVerifier.getDefaultDigestAlgorithm())
				.tokenExtractionStategy(tokenExtractionStategy)
				.certificateSourceTypes(validationContext.getCertificateSourceTypes()).trustedCertificateSources(certificateVerifier.getTrustedCertSources())
				.validationDate(getValidationTime()).foundSignatures(signatures)
				.completeCRLSource(listCRLSource).completeOCSPSource(listOCSPSource);
	}
	
	/**
	 * Sets revocation sources for a following certificate validation
	 * 
	 * @param listCRLSource         {@link ListRevocationSource}
	 * @param listOCSPSource        {@link ListRevocationSource}
	 * @param listCertificateSource {@link ListCertificateSource}
	 */
	protected void prepareCertificateVerifier(ListRevocationSource<CRL> listCRLSource, ListRevocationSource<OCSP> listOCSPSource,
			ListCertificateSource listCertificateSource) {
		certificateVerifier.setSignatureCRLSource(listCRLSource);
		certificateVerifier.setSignatureOCSPSource(listOCSPSource);
		certificateVerifier.setSignatureCertificateSource(listCertificateSource);
	}

	/**
	 * For all signatures to be validated this method merges the CRL sources.
	 *
	 * @param allSignatureList   {@code Collection} of {@code AdvancedSignature}s to
	 *                           validate including the counter-signatures
	 * @param detachedTimestamps   {@code Collection} of {@code TimestampToken}s
	 *                           detached to a validating file
	 * @return merged CRL Source
	 */
	protected ListRevocationSource<CRL> mergeCRLSources(final Collection<AdvancedSignature> allSignatureList, Collection<TimestampToken> detachedTimestamps) {
		ListRevocationSource<CRL> allCrlSource = new ListRevocationSource<CRL>();
		if (Utils.isCollectionNotEmpty(allSignatureList)) {
			for (final AdvancedSignature signature : allSignatureList) {
				allCrlSource.add(signature.getCRLSource());
				allCrlSource.addAll(signature.getTimestampSource().getTimestampCRLSources());
			}
		}
		if (Utils.isCollectionNotEmpty(detachedTimestamps)) {
			for (TimestampToken timestampToken : detachedTimestamps) {
				allCrlSource.add(timestampToken.getCRLSource());
			}
		}
		return allCrlSource;
	}

	/**
	 * For all signatures to be validated this method merges the OCSP sources.
	 *
	 * @param allSignatureList   {@code Collection} of {@code AdvancedSignature}s to
	 *                           validate including the counter-signatures
	 * @param detachedTimestamps   {@code Collection} of {@code TimestampToken}s
	 *                           detached to a validating file
	 * @return merged OCSP Source
	 */
	protected ListRevocationSource<OCSP> mergeOCSPSources(final Collection<AdvancedSignature> allSignatureList, Collection<TimestampToken> detachedTimestamps) {
		ListRevocationSource<OCSP> allOcspSource = new ListRevocationSource<OCSP>();
		if (Utils.isCollectionNotEmpty(allSignatureList)) {
			for (final AdvancedSignature signature : allSignatureList) {
				allOcspSource.add(signature.getOCSPSource());
				allOcspSource.addAll(signature.getTimestampSource().getTimestampOCSPSources());
			}
		}
		if (Utils.isCollectionNotEmpty(detachedTimestamps)) {
			for (TimestampToken timestampToken : detachedTimestamps) {
				allOcspSource.add(timestampToken.getOCSPSource());
			}
		}
		return allOcspSource;
	}
	
	/**
	 * For all signatures to be validated this method merges the Certificate
	 * sources.
	 *
	 * @param allSignatureList   {@code Collection} of {@code AdvancedSignature}s to
	 *                           validate including the counter-signatures
	 * @param detachedTimestamps {@code Collection} of {@code TimestampToken}s
	 *                           detached to a validating file
	 * @return merged Certificate Source
	 */
	protected ListCertificateSource mergeCertificateSource(
			final Collection<AdvancedSignature> allSignatureList,
			Collection<TimestampToken> detachedTimestamps) {
		ListCertificateSource allCertificatesSource = new ListCertificateSource();
		if (Utils.isCollectionNotEmpty(allSignatureList)) {
			for (AdvancedSignature advancedSignature : allSignatureList) {
				allCertificatesSource.addAll(advancedSignature.getCompleteCertificateSource());
			}
		}
		if (Utils.isCollectionNotEmpty(detachedTimestamps)) {
			for (TimestampToken timestampToken : detachedTimestamps) {
				allCertificatesSource.add(timestampToken.getCertificateSource());
			}
		}
		return allCertificatesSource;
	}

	@Override
	public void prepareSignatureValidationContext(final ValidationContext validationContext, final List<AdvancedSignature> allSignatureList) {
		prepareCertificatesAndTimestamps(validationContext, allSignatureList);
		processSignaturesValidation(allSignatureList);
	}

	/**
	 * @param allSignatureList  {@code List} of {@code AdvancedSignature}s to
	 *                          validate including the countersignatures
	 * @param validationContext {@code ValidationContext} is the implementation of
	 *                          the validators for: certificates, timestamps and
	 *                          revocation data.
	 */
	protected void prepareCertificatesAndTimestamps(final ValidationContext validationContext, final List<AdvancedSignature> allSignatureList) {
		if (providedSigningCertificateToken != null) {
			validationContext.addCertificateTokenForVerification(providedSigningCertificateToken);
		}
		for (final AdvancedSignature signature : allSignatureList) {
			ListCertificateSource completeCertificateSource = signature.getCompleteCertificateSource();
			for (CertificateToken certificateToken : completeCertificateSource.getAllCertificateTokens()) {
				validationContext.addCertificateTokenForVerification(certificateToken);
			}
			signature.prepareTimestamps(validationContext);
		}
	}

	/**
	 * Prepares the {@code validationContext} for a timestamp validation process
	 * 
	 * @param validationContext {@link ValidationContext}
	 * @param timestamps        a list of timestamps
	 */
	@Override
	public void prepareDetachedTimestampValidationContext(final ValidationContext validationContext, List<TimestampToken> timestamps) {
		for (TimestampToken timestampToken : timestamps) {
			validationContext.addTimestampTokenForVerification(timestampToken);
			injectReferences(timestampToken);
		}
	}

	/**
	 * TODO : remove
	 * 
	 * Sets the timestamped references based on the given signature scope
	 * 
	 * @param timestamp the timestamp to fill
	 */
	private void injectReferences(TimestampToken timestamp) {
		for (SignatureScope scope : timestamp.getTimestampScopes()) {
			timestamp.getTimestampedReferences().add(new TimestampedReference(scope.getDSSIdAsString(), TimestampedObjectType.SIGNED_DATA));
		}
	}

	/**
	 * Process the validation
	 * 
	 * @param validationContext {@link ValidationContext} to process
	 */
	protected void validateContext(final ValidationContext validationContext) {
		validationContext.initialize(certificateVerifier);
		validationContext.validate();
	}
	
	@Override
	public void setSignaturePolicyProvider(SignaturePolicyProvider signaturePolicyProvider) {
		this.signaturePolicyProvider = signaturePolicyProvider;
	}

	/**
	 * Returns a signaturePolicyProvider If not defined, returns a default provider
	 * 
	 * @return {@link SignaturePolicyProvider}
	 */
	protected SignaturePolicyProvider getSignaturePolicyProvider() {
		if (signaturePolicyProvider == null) {
			signaturePolicyProvider = new SignaturePolicyProvider();
			signaturePolicyProvider.setDataLoader(certificateVerifier.getDataLoader());
		}
		return signaturePolicyProvider;
	}
	
	@Override
	public void setProcessExecutor(final DocumentProcessExecutor processExecutor) {
		this.processExecutor = processExecutor;
	}
	
	/**
	 * This method returns the process executor. If the instance of this class is
	 * not yet instantiated then the new instance is created.
	 *
	 * @return {@code SignatureProcessExecutor}
	 */
	protected DocumentProcessExecutor provideProcessExecutorInstance() {
		if (processExecutor == null) {
			processExecutor = getDefaultProcessExecutor();
		}
		return processExecutor;
	}

	@Override
	public DocumentProcessExecutor getDefaultProcessExecutor() {
		return new DefaultSignatureProcessExecutor();
	}

	/**
	 * Executes the validation regarding to the given {@code validationPolicy}
	 * 
	 * @param diagnosticData   {@link DiagnosticData} contained a data to be
	 *                         validated
	 * @param validationPolicy {@link ValidationPolicy}
	 * @return validation {@link Reports}
	 */
	protected final Reports processValidationPolicy(XmlDiagnosticData diagnosticData, ValidationPolicy validationPolicy) {
		final DocumentProcessExecutor executor = provideProcessExecutorInstance();
		executor.setValidationPolicy(validationPolicy);
		executor.setValidationLevel(validationLevel);
		executor.setDiagnosticData(diagnosticData);
		executor.setIncludeSemantics(includeSemantics);
		executor.setEnableEtsiValidationReport(enableEtsiValidationReport);
		executor.setLocale(locale);
		executor.setCurrentTime(getValidationTime());
		return executor.execute();
	}

	protected List<AdvancedSignature> getAllSignatures() {

		setSignedScopeFinderDefaultDigestAlgorithm(certificateVerifier.getDefaultDigestAlgorithm());

		final List<AdvancedSignature> allSignatureList = new ArrayList<>();
		for (final AdvancedSignature signature : getSignatures()) {
			allSignatureList.add(signature);
			allSignatureList.addAll(signature.getCounterSignatures());			
		}
		
		// Signature Scope must be processed before in order to properly initialize content timestamps
		// TODO change this
		findSignatureScopes(allSignatureList);
		
		return allSignatureList;
	}
	
	@Override
	public List<AdvancedSignature> getSignatures() {
		// delegated in CommonSignatureValidator
		return Collections.emptyList();
	}

	@Override
	public List<TimestampToken> getDetachedTimestamps() {
		// not implemented by default
		// requires an implementation of {@code SignatureValidator}
		return Collections.emptyList();
	}

	@Override
	public void processSignaturesValidation(List<AdvancedSignature> allSignatureList) {
		for (final AdvancedSignature signature : allSignatureList) {
			signature.checkSignatureIntegrity();
			signature.validateStructure();
			signature.checkSignaturePolicy(getSignaturePolicyProvider());
		}
	}

	/**
	 * Finds and assigns SignatureScopes for a list of signatures
	 * 
	 * @param allSignatures
	 *                      a list of {@link AdvancedSignature}s to get a
	 *                      SignatureScope list
	 */
	@Override
	public void findSignatureScopes(List<AdvancedSignature> allSignatures) {
		for (final AdvancedSignature signature : allSignatures) {
			signature.findSignatureScope(signatureScopeFinder);
		}
	}

	public void setSkipValidationContextExecution(boolean skipValidationContextExecution) {
		this.skipValidationContextExecution = skipValidationContextExecution;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

}

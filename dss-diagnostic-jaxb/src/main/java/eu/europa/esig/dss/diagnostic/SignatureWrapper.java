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
package eu.europa.esig.dss.diagnostic;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import eu.europa.esig.dss.diagnostic.jaxb.XmlBasicSignature;
import eu.europa.esig.dss.diagnostic.jaxb.XmlChainItem;
import eu.europa.esig.dss.diagnostic.jaxb.XmlCommitmentTypeIndication;
import eu.europa.esig.dss.diagnostic.jaxb.XmlDigestMatcher;
import eu.europa.esig.dss.diagnostic.jaxb.XmlFoundTimestamp;
import eu.europa.esig.dss.diagnostic.jaxb.XmlPDFRevision;
import eu.europa.esig.dss.diagnostic.jaxb.XmlPolicy;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignature;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignatureDigestReference;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignatureScope;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignerDocumentRepresentations;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignerInfo;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSignerRole;
import eu.europa.esig.dss.diagnostic.jaxb.XmlSigningCertificate;
import eu.europa.esig.dss.diagnostic.jaxb.XmlStructuralValidation;
import eu.europa.esig.dss.enumerations.DigestMatcherType;
import eu.europa.esig.dss.enumerations.EndorsementType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.TimestampLocation;
import eu.europa.esig.dss.enumerations.TimestampType;

public class SignatureWrapper extends AbstractTokenProxy {

	private final XmlSignature signature;
	
	public SignatureWrapper(XmlSignature signature) {
		Objects.requireNonNull(signature, "XmlSignature cannot be null!");
		this.signature = signature;
	}

	@Override
	public String getId() {
		return signature.getId();
	}

	public String getDAIdentifier() {
		return signature.getDAIdentifier();
	}

	@Override
	public List<XmlDigestMatcher> getDigestMatchers() {
		return signature.getDigestMatchers();
	}

	public XmlDigestMatcher getMessageDigest() {
		List<XmlDigestMatcher> digestMatchers = signature.getDigestMatchers();
		for (XmlDigestMatcher xmlDigestMatcher : digestMatchers) {
			if (DigestMatcherType.MESSAGE_DIGEST == xmlDigestMatcher.getType()) {
				return xmlDigestMatcher;
			}
		}
		return null;
	}

	@Override
	protected XmlBasicSignature getCurrentBasicSignature() {
		return signature.getBasicSignature();
	}

	@Override
	protected List<XmlChainItem> getCurrentCertificateChain() {
		return signature.getCertificateChain();
	}

	@Override
	protected XmlSigningCertificate getCurrentSigningCertificate() {
		return signature.getSigningCertificate();
	}

	/**
	 * Returns FoundCertificatesProxy to access embedded certificates
	 * 
	 * @return {@link FoundCertificatesProxy}
	 */
	@Override
	public FoundCertificatesProxy foundCertificates() {
		return new FoundCertificatesProxy(signature.getFoundCertificates());
	}

	/**
	 * Returns FoundRevocationsProxy to access embedded revocation data
	 * 
	 * @return {@link FoundRevocationsProxy}
	 */
	@Override
	public FoundRevocationsProxy foundRevocations() {
		return new FoundRevocationsProxy(signature.getFoundRevocations());
	}

	public String getSignatureFilename() {
		return signature.getSignatureFilename();
	}

	public boolean isStructuralValidationValid() {
		return (signature.getStructuralValidation() != null) && signature.getStructuralValidation().isValid();
	}

	public String getStructuralValidationMessage() {
		XmlStructuralValidation structuralValidation = signature.getStructuralValidation();
		if (structuralValidation != null) {
			return structuralValidation.getMessage();
		}
		return "";
	}

	public Date getClaimedSigningTime() {
		return signature.getClaimedSigningTime();
	}

	public String getContentType() {
		return signature.getContentType();
	}

	public String getMimeType() {
		return signature.getMimeType();
	}

	public String getContentHints() {
		return signature.getContentHints();
	}

	public String getContentIdentifier() {
		return signature.getContentIdentifier();
	}

	public boolean isCounterSignature() {
		return signature.isCounterSignature() != null && signature.isCounterSignature();
	}
	
	public boolean isSignatureDuplicated() {
		return signature.isDuplicated() != null && signature.isDuplicated();
	}
	
	public XmlSignatureDigestReference getSignatureDigestReference() {
		return signature.getSignatureDigestReference();
	}

	public List<TimestampWrapper> getTimestampList() {
		List<TimestampWrapper> tsps = new ArrayList<>();
		List<XmlFoundTimestamp> foundTimestamps = signature.getFoundTimestamps();
		for (XmlFoundTimestamp xmlFoundTimestamp : foundTimestamps) {
			tsps.add(new TimestampWrapper(xmlFoundTimestamp.getTimestamp()));
		}
		return tsps;
	}

	public List<TimestampWrapper> getTimestampListByType(final TimestampType timestampType) {
		List<TimestampWrapper> result = new ArrayList<>();
		List<TimestampWrapper> all = getTimestampList();
		for (TimestampWrapper tsp : all) {
			if (timestampType.equals(tsp.getType())) {
				result.add(tsp);
			}
		}
		return result;
	}
	
	public List<TimestampWrapper> getTimestampListByLocation(TimestampLocation timestampLocation) {
		List<TimestampWrapper> tsps = new ArrayList<>();
		List<XmlFoundTimestamp> foundTimestamps = signature.getFoundTimestamps();
		for (XmlFoundTimestamp xmlFoundTimestamp : foundTimestamps) {
			if (xmlFoundTimestamp.getLocation() != null && 
					xmlFoundTimestamp.getLocation().name().equals(timestampLocation.name())) {
				tsps.add(new TimestampWrapper(xmlFoundTimestamp.getTimestamp()));
			}
		}
		return tsps;
	}

	public boolean isSignatureProductionPlacePresent() {
		return signature.getSignatureProductionPlace() != null;
	}

	public String getAddress() {
		if (isSignatureProductionPlacePresent()) {
			return signature.getSignatureProductionPlace().getAddress();
		}
		return null;
	}

	public String getCity() {
		if (isSignatureProductionPlacePresent()) {
			return signature.getSignatureProductionPlace().getCity();
		}
		return null;
	}

	public String getCountryName() {
		if (isSignatureProductionPlacePresent()) {
			return signature.getSignatureProductionPlace().getCountryName();
		}
		return null;
	}

	public String getPostalCode() {
		if (isSignatureProductionPlacePresent()) {
			return signature.getSignatureProductionPlace().getPostalCode();
		}
		return null;
	}

	public String getStateOrProvince() {
		if (isSignatureProductionPlacePresent()) {
			return signature.getSignatureProductionPlace().getStateOrProvince();
		}
		return null;
	}

	public SignatureLevel getSignatureFormat() {
		return signature.getSignatureFormat();
	}

	public String getErrorMessage() {
		return signature.getErrorMessage();
	}

	public boolean isSigningCertificateIdentified() {
		CertificateWrapper signingCertificate = getSigningCertificate();
		CertificateRefWrapper signingCertificateReference = getSigningCertificateReference();
		if (signingCertificate != null && signingCertificateReference != null) {
			return signingCertificateReference.isDigestValueMatch() && 
					(!signingCertificateReference.isIssuerSerialPresent() || signingCertificateReference.isIssuerSerialMatch());
		}
		return false;
	}

	public String getPolicyId() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.getId();
		}
		return "";
	}

	public boolean isZeroHashPolicy() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.isZeroHash() != null && policy.isZeroHash();
		}
		return false;
	}

	public boolean isBLevelTechnicallyValid() {
		return isSignatureValid();
	}

	public boolean isThereXLevel() {
		List<TimestampWrapper> timestampLevelX = getTimestampLevelX();
		return timestampLevelX != null && timestampLevelX.size() > 0;
	}

	public boolean isXLevelTechnicallyValid() {
		List<TimestampWrapper> timestamps = getTimestampLevelX();
		return isTimestampValid(timestamps);
	}

	public List<TimestampWrapper> getTimestampLevelX() {
		List<TimestampWrapper> timestamps = getTimestampListByType(TimestampType.VALIDATION_DATA_REFSONLY_TIMESTAMP);
		timestamps.addAll(getTimestampListByType(TimestampType.VALIDATION_DATA_TIMESTAMP));
		return timestamps;
	}

	public boolean isThereALevel() {
		List<TimestampWrapper> timestampList = getArchiveTimestamps();
		return timestampList != null && timestampList.size() > 0;
	}

	public boolean isALevelTechnicallyValid() {
		List<TimestampWrapper> timestampList = getArchiveTimestamps();
		return isTimestampValid(timestampList);
	}

	public List<TimestampWrapper> getArchiveTimestamps() {
		return getTimestampListByType(TimestampType.ARCHIVE_TIMESTAMP);
	}

	public boolean isThereTLevel() {
		List<TimestampWrapper> timestamps = getSignatureTimestamps();
		return timestamps != null && timestamps.size() > 0;
	}

	public boolean isTLevelTechnicallyValid() {
		List<TimestampWrapper> timestampList = getSignatureTimestamps();
		return isTimestampValid(timestampList);
	}

	public List<TimestampWrapper> getContentTimestamps() {
		List<TimestampWrapper> timestamps = getTimestampListByType(TimestampType.CONTENT_TIMESTAMP);
		timestamps.addAll(getTimestampListByType(TimestampType.INDIVIDUAL_DATA_OBJECTS_TIMESTAMP));
		timestamps.addAll(getTimestampListByType(TimestampType.ALL_DATA_OBJECTS_TIMESTAMP));
		return timestamps;
	}

	public List<TimestampWrapper> getAllTimestampsProducedAfterSignatureCreation() {
		List<TimestampWrapper> timestamps = new ArrayList<>();
		for (TimestampType timestampType : TimestampType.values()) {
			if (!timestampType.isContentTimestamp()) {
				timestamps.addAll(getTimestampListByType(timestampType));
			}
		}
		return timestamps;
	}

	public List<TimestampWrapper> getSignatureTimestamps() {
		return getTimestampListByType(TimestampType.SIGNATURE_TIMESTAMP);
	}

	private boolean isTimestampValid(List<TimestampWrapper> timestampList) {
		for (final TimestampWrapper timestamp : timestampList) {
			final boolean signatureValid = timestamp.isSignatureValid();
			final XmlDigestMatcher messageImprint = timestamp.getMessageImprint();
			final boolean messageImprintIntact = messageImprint.isDataFound() && messageImprint.isDataIntact();
			if (signatureValid && messageImprintIntact) {
				return true;
			}
		}
		return false;
	}

	public List<String> getTimestampIdsList() {
		List<String> result = new ArrayList<>();
		List<TimestampWrapper> timestamps = getTimestampList();
		if (timestamps != null) {
			for (TimestampWrapper tsp : timestamps) {
				result.add(tsp.getId());
			}
		}
		return result;
	}

	public SignatureWrapper getParent() {
		XmlSignature parent = signature.getParent();
		if (parent != null) {
			return new SignatureWrapper(parent);
		}
		return null;
	}

	public List<XmlSignatureScope> getSignatureScopes() {
		return signature.getSignatureScopes();
	}

	/**
	 * Returns list of all found SignerRoles
	 * @return list of {@link XmlSignerRole}s
	 */
	public List<XmlSignerRole> getSignerRoles() {
		return signature.getSignerRole();
	}

	/**
	 * Returns list of found ClaimedRoles
	 * @return list of {@link XmlSignerRole}s
	 */
	public List<XmlSignerRole> getClaimedRoles() {
		return getSignerRolesByCategory(EndorsementType.CLAIMED);
	}

	/**
	 * Returns list of found CertifiedRoles
	 * @return list of {@link XmlSignerRole}s
	 */
	public List<XmlSignerRole> getCertifiedRoles() {
		return getSignerRolesByCategory(EndorsementType.CERTIFIED);
	}
	
	/**
	 * Returns list of all found SignedAssertions
	 * 
	 * @return list of {@link XmlSignerRole}s
	 */
	public List<XmlSignerRole> getSignedAssertions() {
		return getSignerRolesByCategory(EndorsementType.SIGNED);
	}

	/**
	 * Returns a list of {@code String}s describing the role for the given
	 * {@code listOfSignerRoles}
	 * 
	 * @param listOfSignerRoles - list of {@link XmlSignerRole} to get string role
	 *                          details from
	 * @return list of role details
	 */
	public List<String> getSignerRoleDetails(List<XmlSignerRole> listOfSignerRoles) {
		List<String> roles = new ArrayList<>();
		for (XmlSignerRole xmlSignerRole : listOfSignerRoles) {
			roles.add(xmlSignerRole.getRole());
		}
		return roles;
	}
	
	private List<XmlSignerRole> getSignerRolesByCategory(EndorsementType category) {
		List<XmlSignerRole> roles = new ArrayList<>();
		for (XmlSignerRole xmlSignerRole : getSignerRoles()) {
			if (category.equals(xmlSignerRole.getCategory())) {
				roles.add(xmlSignerRole);
			}
		}
		return roles;
	}

	public List<XmlCommitmentTypeIndication> getCommitmentTypeIndications() {
		List<XmlCommitmentTypeIndication> commitmentTypeIndications = signature.getCommitmentTypeIndications();
		if (commitmentTypeIndications != null) {
			return commitmentTypeIndications;
		}
		return Collections.emptyList();
	}

	public boolean isPolicyPresent() {
		return signature.getPolicy() != null;
	}

	public String getPolicyProcessingError() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.getProcessingError();
		}
		return "";
	}

	public boolean getPolicyStatus() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.isStatus();
		}
		return false;
	}
	
	/**
	 * Returns XMLPolicy description if it is not empty
	 * @return {@link String}
	 */
	public String getPolicyDescription() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null && policy.getDescription() != null) {
			return policy.getDescription();
		}
		return "";
	}
	
	/**
	 * Returns DocumentationReferences defined for the signature policy
	 * @return a list of {@link String}s
	 */
	public List<String> getPolicyDocumentationReferences() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null && policy.getDocumentationReferences() != null) {
			return policy.getDocumentationReferences();
		}
		return Collections.emptyList();
	}

	public String getPolicyNotice() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.getNotice();
		}
		return "";
	}

	public String getPolicyUrl() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.getUrl();
		}
		return "";
	}

	public boolean isPolicyAsn1Processable() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.isAsn1Processable() != null && policy.isAsn1Processable();
		}
		return false;
	}

	public boolean isPolicyIdentified() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.isIdentified() != null && policy.isIdentified();
		}
		return false;
	}

	public boolean isPolicyStatus() {
		XmlPolicy policy = signature.getPolicy();
		if (policy != null) {
			return policy.isStatus() != null && policy.isStatus();
		}
		return false;
	}
	
	/**
	 * Returns a PAdES-specific PDF Revision info
	 * NOTE: applicable only for PAdES
	 * 
	 * @return {@link XmlPDFRevision}
	 */
	public XmlPDFRevision getPDFRevision() {
		return signature.getPDFRevision();
	}
	
	/**
	 * Returns the first signature field name
	 * 
	 * @return {@link String} field name
	 */
	public String getFirstFieldName() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getSignatureFieldName().get(0);
		}
		return null;
	}
	
	/**
	 * Returns a list of signature field names, where the signature is referenced from
	 * 
	 * @return a list of {@link String} signature field names
	 */
	public List<String> getSignatureFieldNames() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getSignatureFieldName();
		}
		return Collections.emptyList();
	}
	
	/**
	 * Returns a list if Signer Infos (Signer Information Store) from CAdES CMS Signed Data
	 * 
	 * @return list of {@link XmlSignerInfo}s
	 */
	public List<XmlSignerInfo> getSignatureInformationStore() {
		return signature.getSignerInformationStore();
	}

	public String getSignerName() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getSignerName();
		}
		return null;
	}

	public String getSignatureDictionaryType() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getType();
		}
		return null;
	}

	public String getFilter() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getFilter();
		}
		return null;
	}

	public String getSubFilter() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getSubFilter();
		}
		return null;
	}

	public String getContactInfo() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getContactInfo();
		}
		return null;
	}

	public String getReason() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getReason();
		}
		return null;
	}
	
	public List<BigInteger> getSignatureByteRange() {
		XmlPDFRevision pdfRevision = signature.getPDFRevision();
		if (pdfRevision != null) {
			return pdfRevision.getPDFSignatureDictionary().getSignatureByteRange();
		}
		return Collections.emptyList();
	}
	
	public byte[] getSignatureValue() {
		return signature.getSignatureValue();
	}
	
	public boolean isDocHashOnly() {
		XmlSignerDocumentRepresentations signerDocumentRepresentation = signature.getSignerDocumentRepresentations();
		if (signerDocumentRepresentation != null) {
			return signerDocumentRepresentation.isDocHashOnly();
		}
		return false;
	}
	
	public boolean isHashOnly() {
		XmlSignerDocumentRepresentations signerDocumentRepresentation = signature.getSignerDocumentRepresentations();
		if (signerDocumentRepresentation != null) {
			return signerDocumentRepresentation.isHashOnly();
		}
		return false;
	}

	@Override
	public byte[] getBinaries() {
		return signature.getSignatureValue();
	}

}

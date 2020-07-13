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
package eu.europa.esig.dss.xades.validation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.xml.security.signature.Reference;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import eu.europa.esig.dss.crl.CRLUtils;
import eu.europa.esig.dss.enumerations.ArchiveTimestampType;
import eu.europa.esig.dss.enumerations.DigestMatcherType;
import eu.europa.esig.dss.enumerations.TimestampLocation;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.enumerations.TimestampedObjectType;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.identifier.Identifier;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSRevocationUtils;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CertificateRef;
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLRef;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPRef;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPResponseBinary;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.ReferenceValidation;
import eu.europa.esig.dss.validation.SignatureProperties;
import eu.europa.esig.dss.validation.scope.SignatureScope;
import eu.europa.esig.dss.validation.timestamp.AbstractTimestampSource;
import eu.europa.esig.dss.validation.timestamp.TimestampInclude;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;
import eu.europa.esig.dss.validation.timestamp.TimestampedReference;
import eu.europa.esig.dss.xades.definition.XAdESNamespaces;
import eu.europa.esig.dss.xades.definition.XAdESPaths;
import eu.europa.esig.dss.xades.definition.xades132.XAdES132Element;
import eu.europa.esig.dss.xades.definition.xades141.XAdES141Element;

@SuppressWarnings("serial")
public class XAdESTimestampSource extends AbstractTimestampSource<XAdESAttribute> {

	private static final Logger LOG = LoggerFactory.getLogger(XAdESTimestampSource.class);
	
	private final transient Element signatureElement;
	private final XAdESPaths xadesPaths;

	private transient List<Reference> references;
	private List<ReferenceValidation> referenceValidations;
	
	private transient XAdESTimestampDataBuilder timestampDataBuilder;
	
	public XAdESTimestampSource(final XAdESSignature signature, final Element signatureElement, 
			final XAdESPaths xadesPaths) {
		super(signature);
		this.references = signature.getReferences();
		this.referenceValidations = signature.getReferenceValidations();
		this.signatureElement = signatureElement;
		this.xadesPaths = xadesPaths;
	}

	@Override
	protected SignatureProperties<XAdESAttribute> getSignedSignatureProperties() {
		return XAdESSignedDataObjectProperties.build(signatureElement, xadesPaths);
	}

	@Override
	protected SignatureProperties<XAdESAttribute> getUnsignedSignatureProperties() {
		return XAdESUnsignedSigProperties.build(signatureElement, xadesPaths);
	}

	@Override
	protected XAdESTimestampDataBuilder getTimestampDataBuilder() {
		if (timestampDataBuilder == null) {
			timestampDataBuilder = new XAdESTimestampDataBuilder(signatureElement, references, xadesPaths);
		}
		return timestampDataBuilder;
	}
	
	/**
	 * Returns concatenated data for a SignatureTimestamp
	 * @param canonicalizationMethod {@link String} canonicalization method to use
	 * @return byte array
	 */
	public byte[] getSignatureTimestampData(String canonicalizationMethod) {
		return timestampDataBuilder.getSignatureTimestampData(canonicalizationMethod);
	}
	
	/**
	 * Returns concatenated data for a SigAndRefsTimestamp
	 * @param canonicalizationMethod {@link String} canonicalization method to use
	 * @return byte array
	 */
	public byte[] getTimestampX1Data(String canonicalizationMethod) {
		return timestampDataBuilder.getTimestampX1Data(canonicalizationMethod);
	}
	
	/**
	 * Returns concatenated data for a RefsOnlyTimestamp
	 * @param canonicalizationMethod {@link String} canonicalization method to use
	 * @return byte array
	 */
	public byte[] getTimestampX2Data(String canonicalizationMethod) {
		return timestampDataBuilder.getTimestampX2Data(canonicalizationMethod);
	}
	
	/**
	 * Returns concatenated data for an ArchiveTimestamp
	 * @param canonicalizationMethod {@link String} canonicalization method to use
	 * @return byte array
	 */
	public byte[] getArchiveTimestampData(String canonicalizationMethod) {
		return timestampDataBuilder.getArchiveTimestampData(canonicalizationMethod);
	}

	@Override
	protected boolean isContentTimestamp(XAdESAttribute signedAttribute) {
		// Not applicable for XAdES
		return false;
	}

	@Override
	protected boolean isAllDataObjectsTimestamp(XAdESAttribute signedAttribute) {
		return XAdES132Element.ALL_DATA_OBJECTS_TIMESTAMP.isSameTagName(signedAttribute.getName());
	}

	@Override
	protected boolean isIndividualDataObjectsTimestamp(XAdESAttribute signedAttribute) {
		return XAdES132Element.INDIVIDUAL_DATA_OBJECTS_TIMESTAMP.isSameTagName(signedAttribute.getName());
	}

	@Override
	protected boolean isSignatureTimestamp(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.SIGNATURE_TIMESTAMP.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isCompleteCertificateRef(XAdESAttribute unsignedAttribute) {
		String localName = unsignedAttribute.getName();
		return XAdES132Element.COMPLETE_CERTIFICATE_REFS.isSameTagName(localName) || XAdES141Element.COMPLETE_CERTIFICATE_REFS_V2.isSameTagName(localName);
	}

	@Override
	protected boolean isAttributeCertificateRef(XAdESAttribute unsignedAttribute) {
		String localName = unsignedAttribute.getName();
		return XAdES132Element.ATTRIBUTE_CERTIFICATE_REFS.isSameTagName(localName) || XAdES141Element.ATTRIBUTE_CERTIFICATE_REFS_V2.isSameTagName(localName);
	}

	@Override
	protected boolean isCompleteRevocationRef(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.COMPLETE_REVOCATION_REFS.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isAttributeRevocationRef(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.ATTRIBUTE_REVOCATION_REFS.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isRefsOnlyTimestamp(XAdESAttribute unsignedAttribute) {
		String localName = unsignedAttribute.getName();
		return XAdES132Element.REFS_ONLY_TIMESTAMP.isSameTagName(localName) || XAdES141Element.REFS_ONLY_TIMESTAMP_V2.isSameTagName(localName);
	}

	@Override
	protected boolean isSigAndRefsTimestamp(XAdESAttribute unsignedAttribute) {
		String localName = unsignedAttribute.getName();
		return XAdES132Element.SIG_AND_REFS_TIMESTAMP.isSameTagName(localName) || XAdES141Element.SIG_AND_REFS_TIMESTAMP_V2.isSameTagName(localName);
	}

	@Override
	protected boolean isCertificateValues(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.CERTIFICATE_VALUES.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isRevocationValues(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.REVOCATION_VALUES.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isAttrAuthoritiesCertValues(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.ATTR_AUTHORITIES_CERT_VALUES.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isAttributeRevocationValues(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.ATTRIBUTE_REVOCATION_VALUES.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isArchiveTimestamp(XAdESAttribute unsignedAttribute) {
		return XAdES132Element.ARCHIVE_TIMESTAMP.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected boolean isTimeStampValidationData(XAdESAttribute unsignedAttribute) {
		return XAdES141Element.TIMESTAMP_VALIDATION_DATA.isSameTagName(unsignedAttribute.getName());
	}

	@Override
	protected TimestampToken makeTimestampToken(XAdESAttribute unsignedAttribute, TimestampType timestampType, 
			List<TimestampedReference> references) {

		final Element timestampTokenNode = unsignedAttribute.findElement(xadesPaths.getCurrentEncapsulatedTimestamp());
		if (timestampTokenNode == null) {
			LOG.warn("The timestamp {} cannot be extracted from the signature!", timestampType.name());
			return null;
		}
		
		TimestampToken timestampToken = null;
		try {
			timestampToken = new TimestampToken(Utils.fromBase64(timestampTokenNode.getTextContent()), timestampType, 
					references, TimestampLocation.XAdES);
		} catch (Exception e) {
			LOG.warn("Unable to build timestamp token from binaries '{}'. Reason : {}", timestampTokenNode.getTextContent(), e.getMessage(), e);
			return null;
		}
		
		timestampToken.setHashCode(unsignedAttribute.getElementHashCode());
		timestampToken.setCanonicalizationMethod(unsignedAttribute.getTimestampCanonicalizationMethod());
		timestampToken.setTimestampIncludes(unsignedAttribute.getTimestampIncludedReferences());
		
		return timestampToken;
		
	}

	@Override
	protected List<TimestampedReference> getIndividualContentTimestampedReferences(XAdESAttribute signedAttribute) {
		List<TimestampInclude> includes = signedAttribute.getTimestampIncludedReferences();
		List<TimestampedReference> timestampReferences = new ArrayList<>();
		for (Reference reference : references) {
			if (isContentTimestampedReference(reference, includes)) {
				for (SignatureScope signatureScope : signatureScopes) {
					if (Utils.endsWithIgnoreCase(reference.getURI(), signatureScope.getName())) {
						addReference(timestampReferences, new TimestampedReference(signatureScope.getDSSIdAsString(), TimestampedObjectType.SIGNED_DATA));
					}
				}
			}
		}
		return timestampReferences;
	}
	
	@Override
	protected List<TimestampedReference> getArchiveTimestampOtherReferences(TimestampToken timestampToken) {
		return getKeyInfoReferences();
	}
	
	private boolean isContentTimestampedReference(Reference reference, List<TimestampInclude> includes) {
		for (TimestampInclude timestampInclude : includes) {
			if (reference.getId().equals(timestampInclude.getURI())) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public List<TimestampedReference> getSignatureTimestampReferences() {
		List<TimestampedReference> timestampedReferences = super.getSignatureTimestampReferences();
		if (isKeyInfoCovered()) {
			addReferences(timestampedReferences, getKeyInfoReferences());
		}
		return timestampedReferences;
	}
	
	protected List<TimestampedReference> getKeyInfoReferences() {
		return createReferencesForCertificates(signatureCertificateSource.getKeyInfoCertificates());
	}

	private boolean isKeyInfoCovered() {
		if (Utils.isCollectionNotEmpty(referenceValidations)) {
			for (ReferenceValidation referenceValidation : referenceValidations) {
				if (DigestMatcherType.KEY_INFO.equals(referenceValidation.getType()) && referenceValidation.isFound() && referenceValidation.isIntact()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected List<CertificateRef> getCertificateRefs(XAdESAttribute unsignedAttribute) {
		List<CertificateRef> certRefs = new ArrayList<>();
		boolean certificateRefV1 = isCertificateRefV1(unsignedAttribute);
		NodeList certRefsNodeList = unsignedAttribute.getNodeList(xadesPaths.getCurrentCertRefsCertChildren());
		for (int ii = 0; ii < certRefsNodeList.getLength(); ii++) {
			Element certRefElement = (Element) certRefsNodeList.item(ii);
			CertificateRef certificateRef = null;
			if (certificateRefV1) {
				certificateRef = XAdESCertificateRefExtractionUtils.createCertificateRefFromV1(certRefElement, xadesPaths);
			} else {
				certificateRef = XAdESCertificateRefExtractionUtils.createCertificateRefFromV2(certRefElement, xadesPaths);
			}
			if (certificateRef != null) {
				certRefs.add(certificateRef);
			}
		}
		return certRefs;
	}

	private boolean isCertificateRefV1(XAdESAttribute unsignedAttribute) {
		String localName = unsignedAttribute.getName();
		return XAdES132Element.ATTRIBUTE_CERTIFICATE_REFS.isSameTagName(localName) || XAdES132Element.COMPLETE_CERTIFICATE_REFS.isSameTagName(localName);
	}

	@Override
	protected List<CRLRef> getCRLRefs(XAdESAttribute unsignedAttribute) {
		List<CRLRef> crlRefs = new ArrayList<>();
		NodeList nodeList = unsignedAttribute.getNodeList(xadesPaths.getCurrentCRLRefsChildren());
		for (int ii = 0; ii < nodeList.getLength(); ii++) {
			Element element = (Element) nodeList.item(ii);
			CRLRef crlRef = XAdESRevocationRefExtractionUtils.createCRLRef(xadesPaths, element);
			if (crlRef != null) {
				crlRefs.add(crlRef);
			}
		}
		return crlRefs;
	}

	@Override
	protected List<OCSPRef> getOCSPRefs(XAdESAttribute unsignedAttribute) {
		List<OCSPRef> ocspRefs = new ArrayList<>();
		NodeList nodeList = unsignedAttribute.getNodeList(xadesPaths.getCurrentOCSPRefsChildren());
		for (int ii = 0; ii < nodeList.getLength(); ii++) {
			Element element = (Element) nodeList.item(ii);
			OCSPRef ocspRef = XAdESRevocationRefExtractionUtils.createOCSPRef(xadesPaths, element);
			if (ocspRef != null) {
				ocspRefs.add(ocspRef);
			}
		}
		return ocspRefs;
	}

	@Override
	protected List<Identifier> getEncapsulatedCertificateIdentifiers(XAdESAttribute unsignedAttribute) {
		List<Identifier> certificateIdentifiers = new ArrayList<>();
		String xPathString = isTimeStampValidationData(unsignedAttribute) ? xadesPaths.getCurrentCertificateValuesEncapsulatedCertificate()
				: xadesPaths.getCurrentEncapsulatedCertificate();
		NodeList encapsulatedNodes = unsignedAttribute.getNodeList(xPathString);
		for (int ii = 0; ii < encapsulatedNodes.getLength(); ii++) {
			try {
				Element element = (Element) encapsulatedNodes.item(ii);
				byte[] binaries = getEncapsulatedTokenBinaries(element);
				CertificateToken certificateToken = DSSUtils.loadCertificate(binaries);
				certificateIdentifiers.add(certificateToken.getDSSId());
			} catch (Exception e) {
				String errorMessage = "Unable to parse an encapsulated certificate : {}";
				if (LOG.isDebugEnabled()) {
					LOG.warn(errorMessage, e.getMessage(), e);
				} else {
					LOG.warn(errorMessage, e.getMessage());
				}
			}
		}
		return certificateIdentifiers;
	}

	@Override
	protected List<Identifier> getEncapsulatedCRLIdentifiers(XAdESAttribute unsignedAttribute) {
		List<Identifier> crlIdentifiers = new ArrayList<>();
		String xPathString = isTimeStampValidationData(unsignedAttribute) ? 
				xadesPaths.getCurrentRevocationValuesEncapsulatedCRLValue() : xadesPaths.getCurrentEncapsulatedCRLValue();
		NodeList encapsulatedNodes = unsignedAttribute.getNodeList(xPathString);
		for (int ii = 0; ii < encapsulatedNodes.getLength(); ii++) {
			try {
				Element element = (Element) encapsulatedNodes.item(ii);
				byte[] binaries = getEncapsulatedTokenBinaries(element);
				crlIdentifiers.add(CRLUtils.buildCRLBinary(binaries));
			} catch (Exception e) {
				String errorMessage = "Unable to parse CRL binaries : {}";
				if (LOG.isDebugEnabled()) {
					LOG.warn(errorMessage, e.getMessage(), e);
				} else {
					LOG.warn(errorMessage, e.getMessage());
				}
			}
		}
		return crlIdentifiers;
	}

	@Override
	protected List<Identifier> getEncapsulatedOCSPIdentifiers(XAdESAttribute unsignedAttribute) {
		List<Identifier> ocspIdentifiers = new ArrayList<>();
		String xPathString = isTimeStampValidationData(unsignedAttribute) ? 
				xadesPaths.getCurrentRevocationValuesEncapsulatedOCSPValue() : xadesPaths.getCurrentEncapsulatedOCSPValue();
		NodeList encapsulatedNodes = unsignedAttribute.getNodeList(xPathString);
		for (int ii = 0; ii < encapsulatedNodes.getLength(); ii++) {
			Element element = (Element) encapsulatedNodes.item(ii);
			byte[] binaries = getEncapsulatedTokenBinaries(element);
			try {
				BasicOCSPResp basicOCSPResp = DSSRevocationUtils.loadOCSPFromBinaries(binaries);
				ocspIdentifiers.add(OCSPResponseBinary.build(basicOCSPResp));
			} catch (IOException e) {
				String errorMessage = "Unable to parse OCSP response binaries : {}";
				if (LOG.isDebugEnabled()) {
					LOG.error(errorMessage, e.getMessage(), e);
				} else {
					LOG.warn(errorMessage, e.getMessage());
				}
			}
		}
		return ocspIdentifiers;
	}
	
	/**
	 * Returns encapsulated byte array from the given {@code encapsulatedElement}
	 * @param encapsulatedElement {@link Element} to get binaries from
	 * @return byte array
	 */
	private byte[] getEncapsulatedTokenBinaries(Element encapsulatedElement) {
		if (encapsulatedElement.hasChildNodes()) {
			Node firstChild = encapsulatedElement.getFirstChild();
			if (Node.TEXT_NODE == firstChild.getNodeType()) {
				String base64String = firstChild.getTextContent();
				if (Utils.isBase64Encoded(base64String)) {
					return Utils.fromBase64(base64String);
				}
			}
		}
		throw new DSSException(String.format("Cannot create the token reference. "
				+ "The element with local name [%s] must contain an encapsulated base64 token value!", encapsulatedElement.getLocalName()));
	}

	@Override
	protected ArchiveTimestampType getArchiveTimestampType(XAdESAttribute unsignedAttribute) {
		if (XAdESNamespaces.XADES_141.isSameUri(unsignedAttribute.getNamespace())) {
			return ArchiveTimestampType.XAdES_141;
		}
		return ArchiveTimestampType.XAdES;
	}

}

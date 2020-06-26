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
package eu.europa.esig.dss.pades.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import eu.europa.esig.dss.cades.validation.CAdESSignature;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureForm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pdf.PAdESConstants;
import eu.europa.esig.dss.pdf.PdfDssDict;
import eu.europa.esig.dss.pdf.PdfSignatureRevision;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.revocation.crl.OfflineCRLSource;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OfflineOCSPSource;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.PdfRevision;
import eu.europa.esig.dss.validation.PdfSignatureDictionary;
import eu.europa.esig.dss.validation.SignatureCertificateSource;
import eu.europa.esig.dss.validation.SignatureDigestReference;
import eu.europa.esig.dss.validation.SignatureIdentifier;
import eu.europa.esig.dss.validation.SignatureProductionPlace;

/**
 * Implementation of AdvancedSignature for PAdES
 */
public class PAdESSignature extends CAdESSignature {

	private static final long serialVersionUID = 3818555396958720967L;

	private final PdfSignatureRevision pdfSignatureRevision;

	// contains a complete list of validating document revisions
	private final List<PdfRevision> documentRevisions;

	/**
	 * The default constructor for PAdESSignature.
	 *
	 * @param pdfSignatureRevision a related {@link PdfSignatureRevision}
	 * @param documentRevisions    a list of {@link PdfRevision} extracted from the
	 *                             validating document
	 * 
	 */
	protected PAdESSignature(final PdfSignatureRevision pdfSignatureRevision, final List<PdfRevision> documentRevisions) {
		super(pdfSignatureRevision.getCMSSignedData(), DSSASN1Utils.getFirstSignerInformation(pdfSignatureRevision.getCMSSignedData()));
		this.pdfSignatureRevision = pdfSignatureRevision;
		this.documentRevisions = documentRevisions;
		this.detachedContents = Arrays.asList(new InMemoryDocument(pdfSignatureRevision.getRevisionCoveredBytes()));
	}

	@Override
	public SignatureForm getSignatureForm() {
		if (hasPKCS7SubFilter()) {
			return SignatureForm.PKCS7;
		}
		return SignatureForm.PAdES;
	}

	@Override
	public SignatureCertificateSource getCertificateSource() {
		if (offlineCertificateSource == null) {
			offlineCertificateSource = new PAdESCertificateSource(pdfSignatureRevision, getSignerInformation());
		}
		return offlineCertificateSource;
	}

	@Override
	public OfflineCRLSource getCRLSource() {
		if (signatureCRLSource == null) {
			signatureCRLSource = new PAdESCRLSource(pdfSignatureRevision.getDssDictionary(), getVRIKey(), getSignerInformation().getSignedAttributes());
		}
		return signatureCRLSource;
	}

	@Override
	public OfflineOCSPSource getOCSPSource() {
		if (signatureOCSPSource == null) {
			signatureOCSPSource = new PAdESOCSPSource(pdfSignatureRevision.getDssDictionary(), getVRIKey(), getSignerInformation().getSignedAttributes());
		}
		return signatureOCSPSource;
	}

	@Override
	public PAdESTimestampSource getTimestampSource() {
		if (signatureTimestampSource == null) {
			signatureTimestampSource = new PAdESTimestampSource(this, documentRevisions);
		}
		return (PAdESTimestampSource) signatureTimestampSource;
	}

	@Override
	public Date getSigningTime() {
		return pdfSignatureRevision.getSigningDate();
	}

	@Override
	public SignatureProductionPlace getSignatureProductionPlace() {
		String location = pdfSignatureRevision.getPdfSigDictInfo().getLocation();
		if (Utils.isStringBlank(location)) {
			return super.getSignatureProductionPlace();
		} else {
			SignatureProductionPlace signatureProductionPlace = new SignatureProductionPlace();
			signatureProductionPlace.setCountryName(location);
			return signatureProductionPlace;
		}
	}

	@Override
	public String getContentIdentifier() {
		return null;
	}

	@Override
	public String getContentHints() {
		return null;
	}

	@Override
	public List<AdvancedSignature> getCounterSignatures() {
		/* Not applicable for PAdES */
		return Collections.emptyList();
	}

	@Override
	public SignatureIdentifier buildSignatureIdentifier() {
		return new PAdESSignatureIdentifier(this);
	}

	/**
	 * TS 119 442 - V1.1.1 - Electronic Signatures and Infrastructures (ESI), ch. 5.1.4.2.1.3 XML component:
	 * 
	 * In case of PAdES signatures, the input of the digest value computation shall be the result of decoding the
	 * hexadecimal string present within the Contents field of the Signature PDF dictionary enclosing one PAdES
	 * digital signature. 
	 */
	@Override
	public SignatureDigestReference getSignatureDigestReference(DigestAlgorithm digestAlgorithm) {
		byte[] contents = getPdfSignatureDictionary().getContents();
		byte[] digestValue = DSSUtils.digest(digestAlgorithm, contents);
		return new SignatureDigestReference(new Digest(digestAlgorithm, digestValue));
	}

	@Override
	public SignatureLevel getDataFoundUpToLevel() {
		if (hasCAdESDetachedSubFilter()) {
			if (hasLTProfile() && hasDSSDictionary()) {
				if (hasLTAProfile()) {
					return SignatureLevel.PAdES_BASELINE_LTA;
				}
				if (hasTProfile()) {
					return SignatureLevel.PAdES_BASELINE_LT;
				}
			}
			if (hasTProfile()) {
				return SignatureLevel.PAdES_BASELINE_T;
			}
			return SignatureLevel.PAdES_BASELINE_B;
		} else if (hasPKCS7SubFilter()) {
			if (hasLTProfile()) {
				if (hasLTAProfile()) {
					return SignatureLevel.PKCS7_LTA;
				}
				if (hasTProfile()) {
					return SignatureLevel.PKCS7_LT;
				}
			}
			if (hasTProfile()) {
				return SignatureLevel.PKCS7_T;
			}
			return SignatureLevel.PKCS7_B;
		} else {
			return SignatureLevel.PDF_NOT_ETSI;
		}
	}

	private boolean hasDSSDictionary() {
		return getDssDictionary() != null;
	}

	public PdfDssDict getDssDictionary() {
		return pdfSignatureRevision.getDssDictionary();
	}

	private boolean hasCAdESDetachedSubFilter() {
		return (pdfSignatureRevision != null) && PAdESConstants.SIGNATURE_DEFAULT_SUBFILTER.equals(pdfSignatureRevision.getPdfSigDictInfo().getSubFilter());
	}

	private boolean hasPKCS7SubFilter() {
		return (pdfSignatureRevision != null) && PAdESConstants.SIGNATURE_PKCS7_SUBFILTER.equals(pdfSignatureRevision.getPdfSigDictInfo().getSubFilter());
	}

	@Override
	public SignatureLevel[] getSignatureLevels() {
		return new SignatureLevel[] { SignatureLevel.PDF_NOT_ETSI, SignatureLevel.PAdES_BASELINE_B, SignatureLevel.PKCS7_B, SignatureLevel.PAdES_BASELINE_T,
				SignatureLevel.PKCS7_T, SignatureLevel.PAdES_BASELINE_LT, SignatureLevel.PKCS7_LT, SignatureLevel.PAdES_BASELINE_LTA,
				SignatureLevel.PKCS7_LTA };
	}

	@Override
	public PdfSignatureRevision getPdfRevision() {
		return pdfSignatureRevision;
	}

	public PdfSignatureDictionary getPdfSignatureDictionary() {
		return pdfSignatureRevision.getPdfSigDictInfo();
	}

	/**
	 * Name of the related to the signature VRI dictionary
	 * @return related {@link String} VRI dictionary name
	 */
	public String getVRIKey() {
		// By ETSI EN 319 142-1 V1.1.1, VRI dictionary's name is the base-16-encoded (uppercase)
		// SHA1 digest of the signature to which it applies
		byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA1, getPdfSignatureDictionary().getContents());
		String vriId = Utils.toHex(digest);
		return vriId.toUpperCase();
	}

}

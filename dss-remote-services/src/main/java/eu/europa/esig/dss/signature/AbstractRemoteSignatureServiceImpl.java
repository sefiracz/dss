package eu.europa.esig.dss.signature;

import java.util.LinkedList;
import java.util.List;

import eu.europa.esig.dss.ASiCContainerType;
import eu.europa.esig.dss.AbstractSignatureParameters;
import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.DSSUtils;
import eu.europa.esig.dss.RemoteCertificate;
import eu.europa.esig.dss.RemoteConverter;
import eu.europa.esig.dss.RemoteSignatureParameters;
import eu.europa.esig.dss.SignatureForm;
import eu.europa.esig.dss.asic.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.asic.ASiCWithXAdESSignatureParameters;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.x509.CertificateToken;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;

public class AbstractRemoteSignatureServiceImpl {

	protected AbstractSignatureParameters getASiCSignatureParameters(ASiCContainerType asicContainerType,
			SignatureForm signatureForm) {
		switch (signatureForm) {
		case CAdES:
			ASiCWithCAdESSignatureParameters asicWithCAdESParameters = new ASiCWithCAdESSignatureParameters();
			asicWithCAdESParameters.aSiC().setContainerType(asicContainerType);
			return asicWithCAdESParameters;
		case XAdES:
			ASiCWithXAdESSignatureParameters asicWithXAdESParameters = new ASiCWithXAdESSignatureParameters();
			asicWithXAdESParameters.aSiC().setContainerType(asicContainerType);
			return asicWithXAdESParameters;
		default:
			throw new DSSException("Unrecognized format (XAdES or CAdES are allowed with ASiC) : " + signatureForm);
		}
	}

	protected AbstractSignatureParameters createParameters(RemoteSignatureParameters remoteParameters) {
		AbstractSignatureParameters parameters = null;
		ASiCContainerType asicContainerType = remoteParameters.getAsicContainerType();
		SignatureForm signatureForm = remoteParameters.getSignatureLevel().getSignatureForm();
		if (asicContainerType != null) {
			parameters = getASiCSignatureParameters(asicContainerType, signatureForm);
		} else {
			switch (signatureForm) {
			case CAdES:
				parameters = new CAdESSignatureParameters();
				break;
			case PAdES:
				PAdESSignatureParameters padesParams = new PAdESSignatureParameters();
				padesParams.setSignatureSize(9472 * 2); // double reserved space for signature
				parameters = padesParams;
				break;
			case XAdES:
				parameters = new XAdESSignatureParameters();
				break;
			default:
				throw new DSSException("Unsupported signature form : " + signatureForm);
			}
		}

		fillParameters(parameters, remoteParameters);

		return parameters;
	}

	protected void fillParameters(AbstractSignatureParameters parameters, RemoteSignatureParameters remoteParameters) {
		parameters.setBLevelParams(remoteParameters.bLevel());
		parameters.setDetachedContents(RemoteConverter.toDSSDocuments(remoteParameters.getDetachedContents()));
		parameters.setDigestAlgorithm(remoteParameters.getDigestAlgorithm());
		parameters.setEncryptionAlgorithm(remoteParameters.getEncryptionAlgorithm());
		parameters.setSignatureLevel(remoteParameters.getSignatureLevel());
		parameters.setSignaturePackaging(remoteParameters.getSignaturePackaging());
		parameters.setSignatureTimestampParameters(remoteParameters.getSignatureTimestampParameters());
		parameters.setArchiveTimestampParameters(remoteParameters.getArchiveTimestampParameters());
		parameters.setContentTimestampParameters(remoteParameters.getContentTimestampParameters());
		parameters.setSignWithExpiredCertificate(remoteParameters.isSignWithExpiredCertificate());

		RemoteCertificate signingCertificate = remoteParameters.getSigningCertificate();
		if (signingCertificate != null) { // extends do not require signing certificate
			CertificateToken loadCertificate = DSSUtils.loadCertificate(signingCertificate.getEncodedCertificate());
			parameters.setSigningCertificate(loadCertificate);
		}

		List<RemoteCertificate> remoteCertificateChain = remoteParameters.getCertificateChain();
		if (Utils.isCollectionNotEmpty(remoteCertificateChain)) {
			List<CertificateToken> certificateChain = new LinkedList<CertificateToken>();
			for (RemoteCertificate remoteCertificate : remoteCertificateChain) {
				certificateChain.add(DSSUtils.loadCertificate(remoteCertificate.getEncodedCertificate()));
			}
			parameters.setCertificateChain(certificateChain);
		}
	}

}

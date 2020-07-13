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
package eu.europa.esig.dss.asic.cades.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.asic.cades.ASiCWithCAdESContainerExtractor;
import eu.europa.esig.dss.asic.common.ASiCUtils;
import eu.europa.esig.dss.asic.common.AbstractASiCContainerExtractor;
import eu.europa.esig.dss.asic.common.validation.AbstractASiCContainerValidator;
import eu.europa.esig.dss.cades.validation.CAdESSignature;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.ArchiveTimestampType;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.DocumentValidator;
import eu.europa.esig.dss.validation.ManifestEntry;
import eu.europa.esig.dss.validation.ManifestFile;
import eu.europa.esig.dss.validation.timestamp.DetachedTimestampValidator;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;

/**
 * This class is an implementation to validate ASiC containers with CAdES signature(s)
 * 
 */
public class ASiCContainerWithCAdESValidator extends AbstractASiCContainerValidator {

	private static final Logger LOG = LoggerFactory.getLogger(ASiCContainerWithCAdESValidator.class);

	ASiCContainerWithCAdESValidator() {
		super(null);
	}

	public ASiCContainerWithCAdESValidator(final DSSDocument asicContainer) {
		super(asicContainer);
		analyseEntries();
	}

	@Override
	public boolean isSupported(DSSDocument dssDocument) {
		return ASiCUtils.isZip(dssDocument) && ASiCUtils.isASiCWithCAdES(dssDocument);
	}

	@Override
	protected AbstractASiCContainerExtractor getArchiveExtractor() {
		return new ASiCWithCAdESContainerExtractor(document);
	}
	
	@Override
	protected List<DocumentValidator> getSignatureValidators() {
		if (signatureValidators == null) {
			signatureValidators = new ArrayList<>();
			for (final DSSDocument signature : getSignatureDocuments()) {
				CMSDocumentForASiCValidator cadesValidator = new CMSDocumentForASiCValidator(signature);
				cadesValidator.setCertificateVerifier(certificateVerifier);
				cadesValidator.setProcessExecutor(processExecutor);
				cadesValidator.setSignaturePolicyProvider(getSignaturePolicyProvider());
				cadesValidator.setDetachedContents(getSignedDocuments(signature));
				cadesValidator.setContainerContents(getArchiveDocuments());
				cadesValidator.setManifestFiles(getManifestFiles());
				signatureValidators.add(cadesValidator);
			}
		}
		return signatureValidators;
	}

	protected List<DocumentValidator> getTimestampValidators() {
		if (timestampValidators == null) {
			timestampValidators = new ArrayList<>();
			for (final DSSDocument timestamp : getTimestampDocuments()) {
				// timestamp's manifest can be a simple ASiCManifest as well as
				// ASiCArchiveManifest file
				DSSDocument archiveManifest = ASiCEWithCAdESManifestParser.getLinkedManifest(getAllManifestDocuments(), timestamp.getName());
				if (archiveManifest != null) {
					ManifestFile validatedManifestFile = getValidatedManifestFile(archiveManifest);
					if (validatedManifestFile != null) {
						ASiCEWithCAdESTimestampValidator timestampValidator = new ASiCEWithCAdESTimestampValidator(timestamp,
								getTimestampType(validatedManifestFile), validatedManifestFile, getAllDocuments());

						timestampValidator.setTimestampedData(archiveManifest);
						timestampValidator.setCertificateVerifier(certificateVerifier);
						timestampValidators.add(timestampValidator);
					} else {
						LOG.warn("A linked manifest is not found for a timestamp with name [{}]!", archiveManifest.getName());
					}
				} else {
					List<DSSDocument> signedDocuments = getSignedDocuments();
					if (Utils.collectionSize(signedDocuments) == 1) {
						DetachedTimestampValidator timestampValidator = new DetachedTimestampValidator(timestamp);
						timestampValidator.setTimestampedData(signedDocuments.get(0));
						timestampValidator.setCertificateVerifier(certificateVerifier);
						timestampValidators.add(timestampValidator);
					} else {
						LOG.warn("Timestamp {} is skipped (no linked archive manifest found / unique file)", timestamp.getName());
					}
				}
			}
		}
		return timestampValidators;
	}
	
	@Override
	public List<TimestampToken> getDetachedTimestamps() {
		List<TimestampToken> independantTimestamps = new ArrayList<>();
		for (DocumentValidator timestampValidator : getTimestampValidators()) {
			independantTimestamps.addAll(timestampValidator.getDetachedTimestamps());
		}
		return independantTimestamps;
	}

	@Override
	public List<DSSDocument> getArchiveDocuments() {
		List<DSSDocument> archiveContents = super.getArchiveDocuments();
		// in case of Manifest file (ASiC-E CAdES signature) add signed documents
		if (Utils.isCollectionNotEmpty(getManifestDocuments())) {
			for (DSSDocument document : getAllDocuments()) {
				if (!archiveContents.contains(document)) {
					archiveContents.add(document);
				}
			}
		}
		return archiveContents;
	}

	@Override
	protected List<TimestampToken> attachExternalTimestamps(List<AdvancedSignature> allSignatures) {
		List<TimestampToken> externalTimestamps = new ArrayList<>();
		
		List<DocumentValidator> currentTimestampValidators = getTimestampValidators();
		for (DocumentValidator tspValidator : currentTimestampValidators) {
			TimestampToken timestamp = getExternalTimestamp(tspValidator, allSignatures);
			if (timestamp != null) {
				externalTimestamps.add(timestamp);
			}
		}

		return externalTimestamps;
	}
	
	private TimestampToken getExternalTimestamp(DocumentValidator tspValidator, List<AdvancedSignature> allSignatures) {

		if (tspValidator instanceof ASiCEWithCAdESTimestampValidator) {

			ASiCEWithCAdESTimestampValidator manifestBasedTimestampValidator = (ASiCEWithCAdESTimestampValidator) tspValidator;

			TimestampToken timestamp = manifestBasedTimestampValidator.getTimestamp();

			ManifestFile coveredManifest = manifestBasedTimestampValidator.getCoveredManifest();
			for (ManifestEntry entry : coveredManifest.getEntries()) {
				for (AdvancedSignature advancedSignature : allSignatures) {
					if (Utils.areStringsEqual(entry.getFileName(), advancedSignature.getSignatureFilename())) {
						CAdESSignature cadesSig = (CAdESSignature) advancedSignature;
						timestamp.setArchiveTimestampType(ArchiveTimestampType.CAdES_DETACHED);
						
						cadesSig.addExternalTimestamp(timestamp);
					}
				}
			}
			return timestamp;
		} else if (tspValidator instanceof DetachedTimestampValidator) {
			return ((DetachedTimestampValidator) tspValidator).getTimestamp();
		}
		
		return null;
	}
	
	private ManifestFile getValidatedManifestFile(DSSDocument manifest) {
		List<ManifestFile> manifestFiles = getManifestFiles();
		if (Utils.isCollectionNotEmpty(manifestFiles)) {
			for (ManifestFile manifestFile : manifestFiles) {
				if (Utils.areStringsEqual(manifest.getName(), manifestFile.getFilename())) {
					return manifestFile;
				}
			}
		}
		return null;
	}
	
	private TimestampType getTimestampType(ManifestFile manifestFile) {
		return coversSignature(manifestFile) ? TimestampType.ARCHIVE_TIMESTAMP : TimestampType.CONTENT_TIMESTAMP;
	}

	/**
	 * Checks if the manifestFile covers a signature
	 * @return TRUE if manifest entries contain a signature, FALSE otherwise
	 */
	private boolean coversSignature(ManifestFile manifestFile) {
		for (ManifestEntry manifestEntry : manifestFile.getEntries()) {
			if (ASiCUtils.isSignature(manifestEntry.getFileName())) {
				return true;
			}
		}
		return false;
	}

	private List<DSSDocument> getSignedDocuments(DSSDocument signature) {
		ASiCContainerType type = getContainerType();
		if (ASiCContainerType.ASiC_S.equals(type)) {
			return getSignedDocuments(); // Collection size should be equal 1
		} else if (ASiCContainerType.ASiC_E.equals(type)) {
			// the manifest file is signed
			// we need first to check the manifest file and its digests
			DSSDocument linkedManifest = ASiCEWithCAdESManifestParser.getLinkedManifest(getManifestDocuments(), signature.getName());
			if (linkedManifest != null) {
				return Arrays.asList(linkedManifest);
			} else {
				return Collections.singletonList(new InMemoryDocument(new byte[] {})); // Force CAdES validation with empty content
			}
		} else {
			LOG.warn("Unknown asic container type (returns all signed documents)");
			return getAllDocuments();
		}
	}

	@Override
	protected List<ManifestFile> getManifestFilesDecriptions() {
		List<ManifestFile> descriptions = new ArrayList<>();
		List<DSSDocument> manifestDocuments = getManifestDocuments();
		for (DSSDocument manifestDocument : manifestDocuments) {
			ManifestFile manifestFile = ASiCEWithCAdESManifestParser.getManifestFile(manifestDocument);
			if (manifestFile != null) {
				ASiCEWithCAdESManifestValidator asiceWithCAdESManifestValidator = new ASiCEWithCAdESManifestValidator(manifestFile, getAllDocuments());
				asiceWithCAdESManifestValidator.validateEntries();
				descriptions.add(manifestFile);
			}
		}

		List<DSSDocument> archiveManifestDocuments = getArchiveManifestDocuments();
		for (DSSDocument manifestDocument : archiveManifestDocuments) {
			ManifestFile manifestFile = ASiCEWithCAdESManifestParser.getManifestFile(manifestDocument);
			if (manifestFile != null) {
				manifestFile.setArchiveManifest(true);
				ASiCEWithCAdESManifestValidator asiceWithCAdESManifestValidator = new ASiCEWithCAdESManifestValidator(manifestFile, getAllDocuments());
				asiceWithCAdESManifestValidator.validateEntries();
				descriptions.add(manifestFile);
			}
		}

		return descriptions;
	}

	@Override
	public List<DSSDocument> getOriginalDocuments(String signatureId) {
		List<AdvancedSignature> signatures = getSignatures();
		for (AdvancedSignature signature : signatures) {
			if (signature.getId().equals(signatureId)) {
				return getOriginalDocuments(signature);
			}
		}
		return Collections.emptyList();
	}
	
	@Override
	public List<DSSDocument> getOriginalDocuments(AdvancedSignature advancedSignature) {
		if (advancedSignature.isCounterSignature()) {
			CAdESSignature cadesSignature = (CAdESSignature) advancedSignature;
			return Arrays.asList(cadesSignature.getOriginalDocument());
		}
		List<DSSDocument> retrievedDocs = advancedSignature.getDetachedContents();
		if (ASiCContainerType.ASiC_S.equals(getContainerType())) {
			return getSignedDocumentsASiCS(retrievedDocs);
		} else {
			DSSDocument linkedManifest = ASiCEWithCAdESManifestParser.getLinkedManifest(getManifestDocuments(), advancedSignature.getSignatureFilename());
			if (linkedManifest == null) {
				return Collections.emptyList();
			}
			ManifestFile manifestFile = ASiCEWithCAdESManifestParser.getManifestFile(linkedManifest);
			List<ManifestEntry> entries = manifestFile.getEntries();
			List<DSSDocument> signedDocuments = getAllDocuments();
			
			List<DSSDocument> result = new ArrayList<>();
			for (ManifestEntry entry : entries) {
				for (DSSDocument signedDocument : signedDocuments) {
					if (Utils.areStringsEqual(entry.getFileName(), signedDocument.getName())) {
						result.add(signedDocument);
					}
				}
			}
			return result;
		}
	}

}

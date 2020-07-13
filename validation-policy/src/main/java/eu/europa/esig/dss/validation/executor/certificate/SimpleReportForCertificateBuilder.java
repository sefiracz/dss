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
package eu.europa.esig.dss.validation.executor.certificate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.europa.esig.dss.detailedreport.DetailedReport;
import eu.europa.esig.dss.detailedreport.jaxb.XmlConclusion;
import eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.jaxb.XmlLangAndValue;
import eu.europa.esig.dss.diagnostic.jaxb.XmlOID;
import eu.europa.esig.dss.diagnostic.jaxb.XmlTrustedService;
import eu.europa.esig.dss.diagnostic.jaxb.XmlTrustedServiceProvider;
import eu.europa.esig.dss.simplecertificatereport.jaxb.XmlChainItem;
import eu.europa.esig.dss.simplecertificatereport.jaxb.XmlRevocation;
import eu.europa.esig.dss.simplecertificatereport.jaxb.XmlSimpleCertificateReport;
import eu.europa.esig.dss.simplecertificatereport.jaxb.XmlSubject;
import eu.europa.esig.dss.simplecertificatereport.jaxb.XmlTrustAnchor;
import eu.europa.esig.dss.utils.Utils;

public class SimpleReportForCertificateBuilder {

	private final DiagnosticData diagnosticData;
	private final DetailedReport detailedReport;
	private final Date currentTime;
	private final String certificateId;

	public SimpleReportForCertificateBuilder(DiagnosticData diagnosticData, DetailedReport detailedReport, Date currentTime, String certificateId) {
		this.diagnosticData = diagnosticData;
		this.detailedReport = detailedReport;
		this.currentTime = currentTime;
		this.certificateId = certificateId;
	}

	public XmlSimpleCertificateReport build() {
		XmlSimpleCertificateReport simpleReport = new XmlSimpleCertificateReport();
		simpleReport.setValidationTime(currentTime);
		List<XmlChainItem> chain = new ArrayList<>();

		CertificateWrapper certificate = diagnosticData.getUsedCertificateById(certificateId);
		XmlChainItem firstChainItem = getChainItem(certificate);
		addQualifications(firstChainItem);
		chain.add(firstChainItem);

		List<CertificateWrapper> certificateChain = certificate.getCertificateChain();
		for (CertificateWrapper cert : certificateChain) {
			chain.add(getChainItem(cert));
		}
		simpleReport.setChain(chain);

		return simpleReport;
	}

	private XmlChainItem getChainItem(CertificateWrapper certificate) {
		XmlChainItem item = new XmlChainItem();
		item.setId(certificate.getId());
		item.setSubject(getSubject(certificate));
		CertificateWrapper signingCertificate = certificate.getSigningCertificate();
		if (signingCertificate != null) {
			item.setIssuerId(signingCertificate.getId());
		}
		item.setNotBefore(certificate.getNotBefore());
		item.setNotAfter(certificate.getNotAfter());
		item.setKeyUsages(certificate.getKeyUsages());
		item.setExtendedKeyUsages(getReadable(certificate.getExtendedKeyUsages()));
		item.setAiaUrls(emptyToNull(certificate.getAuthorityInformationAccessUrls()));
		item.setOcspUrls(emptyToNull(certificate.getOCSPAccessUrls()));
		item.setCrlUrls(emptyToNull(certificate.getCRLDistributionPoints()));
		item.setCpsUrls(emptyToNull(certificate.getCpsUrls()));
		item.setPdsUrls(null);

		XmlRevocation revocation = new XmlRevocation();
		CertificateRevocationWrapper revocationData = diagnosticData.getLatestRevocationDataForCertificate(certificate);
		if (revocationData != null) {
			revocation.setProductionDate(revocationData.getProductionDate());
			revocation.setRevocationDate(revocationData.getRevocationDate());
			revocation.setRevocationReason(revocationData.getReason());
		}
		item.setRevocation(revocation);

		if (certificate.isTrusted()) {
			List<XmlTrustedServiceProvider> trustServiceProviders = filterByCertificateId(certificate.getTrustServiceProviders(), certificate.getId());
			List<XmlTrustAnchor> trustAnchors = new ArrayList<>();
			for (XmlTrustedServiceProvider xmlTrustedServiceProvider : trustServiceProviders) {
				List<XmlTrustedService> trustedServices = xmlTrustedServiceProvider.getTrustedServices();
				Set<String> uniqueServiceNames = getUniqueServiceNames(trustedServices);
				for (String serviceName : uniqueServiceNames) {
					XmlTrustAnchor trustAnchor = new XmlTrustAnchor();
					trustAnchor.setCountryCode(xmlTrustedServiceProvider.getTL().getCountryCode());
					trustAnchor.setTrustServiceProvider(getFirst(xmlTrustedServiceProvider.getTSPNames()));
					List<String> tspRegistrationIdentifiers = xmlTrustedServiceProvider.getTSPRegistrationIdentifiers();
					if (Utils.isCollectionNotEmpty(tspRegistrationIdentifiers)) {
						trustAnchor.setTrustServiceProviderRegistrationId(tspRegistrationIdentifiers.get(0));
					}
					trustAnchor.setTrustServiceName(serviceName);
					trustAnchors.add(trustAnchor);
				}
			}
			item.setTrustAnchors(trustAnchors);
		} else {
			item.setTrustAnchors(null);
		}

		XmlConclusion conclusion = detailedReport.getCertificateXCVConclusion(certificate.getId());
		item.setIndication(conclusion.getIndication());
		item.setSubIndication(conclusion.getSubIndication());

		return item;
	}

	private String getFirst(List<XmlLangAndValue> langAndValues) {
		if (Utils.isCollectionNotEmpty(langAndValues)) {
			return langAndValues.get(0).getValue();
		}
		return null;
	}

	private List<XmlTrustedServiceProvider> filterByCertificateId(List<XmlTrustedServiceProvider> trustServiceProviders, String certificateId) {
		List<XmlTrustedServiceProvider> result = new ArrayList<>();
		for (XmlTrustedServiceProvider xmlTrustedServiceProvider : trustServiceProviders) {
			List<XmlTrustedService> trustedServices = xmlTrustedServiceProvider.getTrustedServices();
			boolean foundCertId = false;
			for (XmlTrustedService xmlTrustedService : trustedServices) {
				if (Utils.areStringsEqual(certificateId, xmlTrustedService.getServiceDigitalIdentifier().getId())) {
					foundCertId = true;
					break;
				}
			}
			if (foundCertId) {
				result.add(xmlTrustedServiceProvider);
			}
		}
		return result;
	}

	private List<String> getReadable(List<XmlOID> oids) {
		if (Utils.isCollectionNotEmpty(oids)) {
			List<String> result = new ArrayList<>();
			for (XmlOID xmlOID : oids) {
				if (Utils.isStringNotEmpty(xmlOID.getDescription())) {
					result.add(xmlOID.getDescription());
				} else {
					result.add(xmlOID.getValue());
				}
			}
			return result;
		}
		return null;
	}

	private Set<String> getUniqueServiceNames(List<XmlTrustedService> trustedServices) {
		Set<String> result = new HashSet<>();
		for (XmlTrustedService xmlTrustedService : trustedServices) {
			result.add(getFirst(xmlTrustedService.getServiceNames()));
		}
		return result;
	}

	private XmlSubject getSubject(CertificateWrapper certificate) {
		XmlSubject subject = new XmlSubject();
		subject.setCommonName(certificate.getCommonName());
		subject.setPseudonym(certificate.getPseudo());
		subject.setSurname(certificate.getSurname());
		subject.setGivenName(certificate.getGivenName());
		subject.setOrganizationName(certificate.getOrganizationName());
		subject.setOrganizationUnit(certificate.getOrganizationalUnit());
		subject.setEmail(certificate.getEmail());
		subject.setLocality(certificate.getLocality());
		subject.setState(certificate.getState());
		subject.setCountry(certificate.getCountryName());
		return subject;
	}

	private List<String> emptyToNull(List<String> listUrls) {
		if (Utils.isCollectionEmpty(listUrls)) {
			return null;
		}
		return listUrls;
	}

	private void addQualifications(XmlChainItem firstChainItem) {
		firstChainItem.setQualificationAtIssuance(detailedReport.getCertificateQualificationAtIssuance(certificateId));
		firstChainItem.setQualificationAtValidation(detailedReport.getCertificateQualificationAtValidation(certificateId));
	}

}

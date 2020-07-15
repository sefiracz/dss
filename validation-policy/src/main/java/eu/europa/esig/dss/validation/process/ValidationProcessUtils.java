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
package eu.europa.esig.dss.validation.process;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import eu.europa.esig.dss.detailedreport.jaxb.XmlBasicBuildingBlocks;
import eu.europa.esig.dss.detailedreport.jaxb.XmlConclusion;
import eu.europa.esig.dss.detailedreport.jaxb.XmlSubXCV;
import eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.i18n.I18nProvider;
import eu.europa.esig.dss.i18n.MessageTag;

public class ValidationProcessUtils {

	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";
	
	/*
	 * RFC 2560 : 4.2.2.2.1  Revocation Checking of an Authorized Responder
	 * 
	 * A CA may specify that an OCSP client can trust a responder for the
	 * lifetime of the responder's certificate. The CA does so by including
	 * the extension id-pkix-ocsp-nocheck.
	 */
	public static boolean isRevocationCheckRequired(CertificateWrapper certificate, Date controlTime) {
		if (certificate.isIdPkixOcspNoCheck()) {
			return !(controlTime.compareTo(certificate.getNotBefore()) >= 0 && controlTime.compareTo(certificate.getNotAfter()) <= 0);
		}
		return true;
	}
	
	/**
	 * Checks if the given conclusion is allowed as a basic signature validation in order to continue
	 * the validation process with Long-Term Validation Data
	 * 
	 * @param conclusion {@link XmlConclusion} to validate
	 * @return TRUE if the result is allowed to continue the validation process, FALSE otherwise
	 */
	public static boolean isAllowedBasicSignatureValidation(XmlConclusion conclusion) {
		return Indication.PASSED.equals(conclusion.getIndication()) || (Indication.INDETERMINATE.equals(conclusion.getIndication())
				&& (SubIndication.CRYPTO_CONSTRAINTS_FAILURE_NO_POE.equals(conclusion.getSubIndication())
						|| SubIndication.REVOKED_NO_POE.equals(conclusion.getSubIndication()) 
						|| SubIndication.REVOKED_CA_NO_POE.equals(conclusion.getSubIndication())
						|| SubIndication.TRY_LATER.equals(conclusion.getSubIndication())
						|| SubIndication.OUT_OF_BOUNDS_NO_POE.equals(conclusion.getSubIndication())
						|| SubIndication.OUT_OF_BOUNDS_NOT_REVOKED.equals(conclusion.getSubIndication())));
	}
	
	/**
	 * Returns a revocation data used for basic signature validation
	 * 
	 * @param certificate {@link CertificateWrapper} to get a latest applicable revocation data for
	 * @param bbb {@link XmlBasicBuildingBlocks} validation of a token
	 * @return {@link CertificateRevocationWrapper}
	 */
	public static CertificateRevocationWrapper getLatestAcceptableRevocationData(CertificateWrapper certificate, XmlBasicBuildingBlocks bbb) {
		if (bbb != null && bbb.getXCV() != null) {
			for (XmlSubXCV subXCV : bbb.getXCV().getSubXCV()) {
				if (certificate.getId().equals(subXCV.getId()) && subXCV.getRFC() != null) {
					return certificate.getRevocationDataById(subXCV.getRFC().getId());
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns a formatted String representation of a given Date
	 * 
	 * @param date {@link Date} to be pretty-printed
	 * @return {@link String} formatted date
	 */
	public static String getFormattedDate(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(date);
	}
	
	/**
	 * Builds a String message from the provided {@code messageTag}
	 * 
	 * @param i18nProvider {@link I18nProvider} to build a message
	 * @param messageTag {@link MessageTag} defining the message to be build
	 * @return final message {@link String}
	 */
	public static String buildStringMessage(I18nProvider i18nProvider, MessageTag messageTag) {
		if (messageTag != null) {
			return i18nProvider.getMessage(messageTag);
		}
		return null;
	}

}

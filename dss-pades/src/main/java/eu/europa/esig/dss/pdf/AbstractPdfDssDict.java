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
package eu.europa.esig.dss.pdf;

import java.util.Map;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;

import eu.europa.esig.dss.crl.CRLBinary;
import eu.europa.esig.dss.model.x509.CertificateToken;

public abstract class AbstractPdfDssDict implements PdfDssDict {

	private final Map<Long, CRLBinary> crlMap;
	private final Map<Long, BasicOCSPResp> ocspMap;
	private final Map<Long, CertificateToken> certMap;

	protected AbstractPdfDssDict(PdfDict dssDictionary) {
		this.certMap = DSSDictionaryExtractionUtils.getCertsFromArray(dssDictionary, getDictionaryName(), getCertArrayDictionaryName());
		this.ocspMap = DSSDictionaryExtractionUtils.getOCSPsFromArray(dssDictionary, getDictionaryName(), getOCSPArrayDictionaryName());
		this.crlMap = DSSDictionaryExtractionUtils.getCRLsFromArray(dssDictionary, getDictionaryName(), getCRLArrayDictionaryName());
	}
	
	protected abstract String getDictionaryName();
	
	protected abstract String getCertArrayDictionaryName();
	
	protected abstract String getCRLArrayDictionaryName();
	
	protected abstract String getOCSPArrayDictionaryName();

	@Override
	public Map<Long, CRLBinary> getCRLs() {
		return crlMap;
	}

	@Override
	public Map<Long, BasicOCSPResp> getOCSPs() {
		return ocspMap;
	}

	@Override
	public Map<Long, CertificateToken> getCERTs() {
		return certMap;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((certMap == null) ? 0 : certMap.hashCode());
		result = prime * result + ((crlMap == null) ? 0 : crlMap.hashCode());
		result = prime * result + ((ocspMap == null) ? 0 : ocspMap.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AbstractPdfDssDict other = (AbstractPdfDssDict) obj;
		if (certMap == null) {
			if (other.certMap != null) {
				return false;
			}
		} else if (!certMap.equals(other.certMap)) {
			return false;
		}
		if (crlMap == null) {
			if (other.crlMap != null) {
				return false;
			}
		} else if (!crlMap.equals(other.crlMap)) {
			return false;
		}
		if (ocspMap == null) {
			if (other.ocspMap != null) {
				return false;
			}
		} else if (!ocspMap.equals(other.ocspMap)) {
			return false;
		}
		return true;
	}


}

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
package eu.europa.esig.dss.tsl.dto;

import java.util.Date;
import java.util.List;

import eu.europa.esig.dss.spi.tsl.OtherTSLPointer;
import eu.europa.esig.dss.spi.tsl.ParsingInfoRecord;
import eu.europa.esig.dss.spi.tsl.TrustService;
import eu.europa.esig.dss.spi.tsl.TrustServiceProvider;
import eu.europa.esig.dss.utils.Utils;

public class ParsingCacheDTO extends AbstractCacheDTO implements ParsingInfoRecord {
	
	private static final long serialVersionUID = 5464908480606825440L;
	
	private Integer sequenceNumber;
	private Integer version;
	private String territory;
	private Date issueDate;
	private Date nextUpdateDate;
	private List<String> distributionPoints;
	private List<TrustServiceProvider> trustServiceProviders;
	private List<OtherTSLPointer> lotlOtherPointers;
	private List<OtherTSLPointer> tlOtherPointers;
	private List<String> pivotUrls;
	private String signingCertificateAnnouncementUrl;

	public ParsingCacheDTO() {}
	
	public ParsingCacheDTO(AbstractCacheDTO cacheDTO) {
		super(cacheDTO);
	}

	@Override
	public Integer getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(Integer sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	@Override
	public Integer getVersion() {
		return version;
	}
	
	public void setVersion(Integer version) {
		this.version = version;
	}

	@Override
	public String getTerritory() {
		return territory;
	}
	
	public void setTerritory(String territory) {
		this.territory = territory;
	}

	@Override
	public Date getIssueDate() {
		return issueDate;
	}
	
	public void setIssueDate(Date issueDate) {
		this.issueDate = issueDate;
	}

	@Override
	public Date getNextUpdateDate() {
		return nextUpdateDate;
	}
	
	public void setNextUpdateDate(Date nextUpdateDate) {
		this.nextUpdateDate = nextUpdateDate;
	}

	@Override
	public List<String> getDistributionPoints() {
		return distributionPoints;
	}
	
	public void setDistributionPoints(List<String> distributionPoints) {
		this.distributionPoints = distributionPoints;
	}

	@Override
	public List<TrustServiceProvider> getTrustServiceProviders() {
		return trustServiceProviders;
	}
	
	public void setTrustServiceProviders(List<TrustServiceProvider> trustServiceProviders) {
		this.trustServiceProviders = trustServiceProviders;
	}

	@Override
	public List<OtherTSLPointer> getLotlOtherPointers() {
		return lotlOtherPointers;
	}
	
	public void setLotlOtherPointers(List<OtherTSLPointer> lotlOtherPointers) {
		this.lotlOtherPointers = lotlOtherPointers;
	}

	@Override
	public List<OtherTSLPointer> getTlOtherPointers() {
		return tlOtherPointers;
	}
	
	public void setTlOtherPointers(List<OtherTSLPointer> tlOtherPointers) {
		this.tlOtherPointers = tlOtherPointers;
	}

	@Override
	public List<String> getPivotUrls() {
		return pivotUrls;
	}

	public void setPivotUrls(List<String> pivotUrls) {
		this.pivotUrls = pivotUrls;
	}

	@Override
	public String getSigningCertificateAnnouncementUrl() {
		return signingCertificateAnnouncementUrl;
	}

	public void setSigningCertificateAnnouncementUrl(String signingCertificateAnnouncementUrl) {
		this.signingCertificateAnnouncementUrl = signingCertificateAnnouncementUrl;
	}

	@Override
	public int getTSPNumber() {
		if (Utils.isCollectionNotEmpty(trustServiceProviders)) {
			return trustServiceProviders.size();
		}
		return 0;
	}

	@Override
	public int getTSNumber() {
		int tsNumber = 0;
		if (Utils.isCollectionNotEmpty(trustServiceProviders)) {
			for (TrustServiceProvider tsp : trustServiceProviders) {
				tsNumber += tsp.getServices().size();
			}
		}
		return tsNumber;
	}

	@Override
	public int getCertNumber() {
		int certNumber = 0;
		if (Utils.isCollectionNotEmpty(trustServiceProviders)) {
			for (TrustServiceProvider tsp : trustServiceProviders) {
				for (TrustService trustService : tsp.getServices()) {
					certNumber += trustService.getCertificates().size();
				}
			}
		}
		return certNumber;
	}

}

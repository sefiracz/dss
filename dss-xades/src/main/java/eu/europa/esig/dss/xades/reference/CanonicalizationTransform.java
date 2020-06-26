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
package eu.europa.esig.dss.xades.reference;

import org.apache.xml.security.signature.XMLSignatureInput;
import org.w3c.dom.Node;

import eu.europa.esig.dss.DomUtils;
import eu.europa.esig.dss.definition.DSSNamespace;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.xades.DSSXMLUtils;
import eu.europa.esig.dss.xades.definition.XAdESNamespaces;

public class CanonicalizationTransform extends ComplexTransform {

	public CanonicalizationTransform(String canonicalizationAlgorithm) {
		this(XAdESNamespaces.XMLDSIG, canonicalizationAlgorithm);
	}

	public CanonicalizationTransform(DSSNamespace xmlDSigNamespace, String canonicalizationAlgorithm) {
		super(xmlDSigNamespace, canonicalizationAlgorithm);
		if (!DSSXMLUtils.canCanonicalize(canonicalizationAlgorithm)) {
			throw new DSSException(String.format("The provided canonicalization method [%s] is not supported!", canonicalizationAlgorithm));
		}
	}
	
	@Override
	protected XMLSignatureInput getXMLSignatureInput(Node node, String uri) {
		XMLSignatureInput xmlSignatureInput = super.getXMLSignatureInput(node, uri);
		xmlSignatureInput.setExcludeComments(isExcludeComments(uri));
		return xmlSignatureInput;
	}
	
	protected boolean isExcludeComments(String uri) {
		// see XMLDSig core 4.4.3.2 The Reference Processing Model and 4.4.3.3 Same-Document URI-References
		// i.e. comments shall be omitted for the same document references
		return uri != null && ("".equals(uri) || DomUtils.isElementReference(uri));
	}

}

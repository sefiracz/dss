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
package eu.europa.esig.dss.jaxb;

import java.util.List;
import java.util.Objects;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

public final class XmlDefinerUtils {
	
	private static XmlDefinerUtils singleton;
	
	private TransformerFactoryBuilder secureTransformerFactoryBuilder = TransformerFactoryBuilder.getSecureTransformerBuilder();
	private SchemaFactoryBuilder secureSchemaFactoryBuilder = SchemaFactoryBuilder.getSecureSchemaBuilder();
	private ValidatorConfigurator secureValidatorConfigurator = ValidatorConfigurator.getSecureValidatorConfigurator();
	
	/**
	 * Instantiate the {@code XmlDefinerUtils}
	 * 
	 * @return {@link XmlDefinerUtils}
	 */
	public static XmlDefinerUtils getInstance() {
		if (singleton == null) {
			singleton = new XmlDefinerUtils();
		}
		return singleton;
	}
	
	/**
	 * Returns a Schema for a list of defined xsdSources
	 * 
	 * @param xsdSources
	 *                   a list of {@link Source}s
	 * @return {@link Schema}
	 * @throws SAXException
	 *                      in case of exception
	 */
	public Schema getSchema(List<Source> xsdSources) throws SAXException {
		Objects.requireNonNull(xsdSources, "XSD Source(s) must be provided");
		SchemaFactory sf = getSecureSchemaFactory();
		return sf.newSchema(xsdSources.toArray(new Source[xsdSources.size()]));
	}
	
	/**
	 * Sets a pre-configured builder to instantiate a {@code SchemaFactory}
	 * 
	 * @param schemaFactoryBuilder {@link SchemaFactoryBuilder}
	 */
	public void setSchemaFactoryBuilder(SchemaFactoryBuilder schemaFactoryBuilder) {
		this.secureSchemaFactoryBuilder = schemaFactoryBuilder;
	}

	/**
	 * Returns a SchemaFactory with enabled security features (disabled external
	 * DTD/XSD + secure processing
	 * 
	 * @return {@link SchemaFactory}
	 * @throws SAXException
	 *                      in case of exception
	 */
	public SchemaFactory getSecureSchemaFactory() throws SAXException {
		return secureSchemaFactoryBuilder.build();
	}
	
	/**
	 * Sets a pre-configured builder to instantiate a {@code TransformerFactory}
	 * 
	 * @param transformerFactoryBuilder {@link TransformerFactoryBuilder}
	 */
	public void setTransformerFactoryBuilder(TransformerFactoryBuilder transformerFactoryBuilder) {
		this.secureTransformerFactoryBuilder = transformerFactoryBuilder;
	}

	/**
	 * Returns a TransformerFactory with enabled security features (disabled
	 * external DTD/XSD + secure processing
	 * 
	 * @return {@link TransformerFactory}
	 */
	public TransformerFactory getSecureTransformerFactory() {
		return secureTransformerFactoryBuilder.build();
	}
	
	/**
	 * Sets a pre-configured builder to instantiate a {@code Validator}
	 * 
	 * @param validatorConfigurator {@link ValidatorConfigurator}
	 */
	public void setValidatorConfigurator(ValidatorConfigurator validatorConfigurator) {
		this.secureValidatorConfigurator = validatorConfigurator;
	}

	/**
	 * The method configures the validator
	 * 
	 * @param validator
	 *                  the validator to be configured
	 * @throws SAXException
	 *                      in case of exception
	 */
	public void configure(Validator validator) throws SAXException {
		secureValidatorConfigurator.configure(validator);
	}

}

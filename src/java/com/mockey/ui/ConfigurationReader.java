/*
 * Copyright 2002-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mockey.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.mockey.model.Scenario;
import com.mockey.model.Service;
import com.mockey.model.ServicePlan;
import com.mockey.storage.IMockeyStorage;
import com.mockey.storage.StorageRegistry;
import com.mockey.storage.xml.MockeyXmlFileConfigurationReader;

/**
 * Consumes an XML file and configures Mockey services.
 * 
 * @author Chad.Lafontaine
 * 
 */
public class ConfigurationReader {

	private static final long serialVersionUID = 2874257060865115637L;
	private static IMockeyStorage store = StorageRegistry.MockeyStorage;

	private static Logger logger = Logger.getLogger(ConfigurationReader.class);

	/**
	 * 
	 * @param file
	 *            - xml configuration file for Mockey
	 * @throws IOException
	 * @throws SAXException 
	 * @throws SAXParseException 
	 */
	public void inputFile(File file) throws IOException, SAXParseException, SAXException {
		InputStream is = new FileInputStream(file);

		long length = file.length();

		if (length > Integer.MAX_VALUE) {

			logger.error("File too large");
		} else {

			// Create the byte array to hold the data
			byte[] bytes = new byte[(int) length];
			// Read in the bytes
			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length
					&& (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
				offset += numRead;
			}

			// Ensure all the bytes have been read in
			if (offset < bytes.length) {
				throw new IOException("Could not completely read file "
						+ file.getName());
			}

			// Close the input stream and return bytes
			is.close();

			loadConfiguration(bytes);
		}

	}

	/**
	 * 
	 * @param data
	 * @return results (conflicts and additions).
	 * @throws IOException
	 * @throws SAXException 
	 * @throws SAXParseException 
	 */
	public ConfigurationReadResults loadConfiguration(byte[] data)
			throws IOException, SAXParseException, SAXException {
		ConfigurationReadResults readResults = new ConfigurationReadResults();

		String strXMLDefintion = new String(data);
		MockeyXmlFileConfigurationReader msfr = new MockeyXmlFileConfigurationReader();
		IMockeyStorage mockServiceStoreTemporary = msfr
				.readDefinition(strXMLDefintion);
		// PROXY SETTINGs
		store.setProxy(mockServiceStoreTemporary.getProxy());

		// UNIVERSAL RESPONSE SETTINGS
		if (store.getUniversalErrorScenario() != null
				&& mockServiceStoreTemporary.getUniversalErrorScenario() != null) {
			readResults
					.addConflictMsg("<b>Universal error message</b>: one already defined with name '"
							+ store.getUniversalErrorScenario()
									.getScenarioName() + "'");
		} else if (store.getUniversalErrorScenario() == null
				&& mockServiceStoreTemporary.getUniversalErrorScenario() != null) {

			store.setUniversalErrorScenarioId(mockServiceStoreTemporary
					.getUniversalErrorScenario().getId());
			store.setUniversalErrorServiceId(mockServiceStoreTemporary
					.getUniversalErrorScenario().getServiceId());
			readResults
					.addAdditionMsg("<b>Universal error response defined.</b>");

		}
		// When loading a definition file, by default, we should
		// compare uploaded Service�s mock URL to what's currently
		// in memory.
		//
		// 1) MATCHING MOCK URL
		// If there is an existing/matching mockURL, then this isn't
		// a new service and we DON'T want to overwrite. But, we
		// want new Scenarios if they exist. A new scenario is based
		// on
		//
		// 2) NO MATCHING MOCK URL
		// If there is no matching service URL, then create a new
		// service and associated scenarios.
		List uploadedServices = mockServiceStoreTemporary.getServices();
		Iterator iter2 = uploadedServices.iterator();
		while (iter2.hasNext()) {
			Service uploadedServiceBean = (Service) iter2.next();
			List serviceBeansInMemory = store.getServices();
			Iterator iter3 = serviceBeansInMemory.iterator();
			boolean existingServiceWithMatchingMockUrl = false;
			Service inMemoryServiceBean = null;
			while (iter3.hasNext()) {
				inMemoryServiceBean = (Service) iter3.next();
				if (inMemoryServiceBean.getMockServiceUrl().equals(
						uploadedServiceBean.getMockServiceUrl())) {
					existingServiceWithMatchingMockUrl = true;
					readResults
							.addConflictMsg("<b>Service not added</b>: Matching mock URL '"
									+ uploadedServiceBean.getMockServiceUrl()
									+ "' with"
									+ " service name '"
									+ uploadedServiceBean.getServiceName()
									+ "'");
					break;
				}
			}
			if (!existingServiceWithMatchingMockUrl) {
				// We null it, to not stomp on any services
				uploadedServiceBean.setId(null);
				store.saveOrUpdateService(uploadedServiceBean);
				readResults.addAdditionMsg("<b>Service Added</b>: '"
						+ uploadedServiceBean.getServiceName() + "'");

			} else {
				// Just save scenarios
				Iterator uIter = uploadedServiceBean.getScenarios().iterator();
				Iterator mIter = inMemoryServiceBean.getScenarios().iterator();
				while (uIter.hasNext()) {
					Scenario uBean = (Scenario) uIter.next();
					boolean existingScenario = false;
					Scenario mBean = null;
					while (mIter.hasNext()) {
						mBean = (Scenario) mIter.next();
						if (mBean.getScenarioName().equals(
								uBean.getScenarioName())) {
							existingScenario = true;
							break;
						}
					}
					if (!existingScenario) {
						uBean.setServiceId(inMemoryServiceBean.getId());
						inMemoryServiceBean.saveOrUpdateScenario(uBean);
						store.saveOrUpdateService(inMemoryServiceBean);
						readResults.addAdditionMsg("<b>Scenario Added</b>: '"
								+ uBean.getScenarioName()
								+ "' added to existing service '"
								+ inMemoryServiceBean.getServiceName() + "'");
					} else {
						readResults
								.addConflictMsg("<b>Scenario not added</b>: '"
										+ mBean.getScenarioName()
										+ "', already defined in service '"
										+ inMemoryServiceBean.getServiceName()
										+ "'");
					}

				}
			}

		}
		// PLANS
		List<ServicePlan> servicePlans = mockServiceStoreTemporary
				.getServicePlans();
		Iterator<ServicePlan> iter3 = servicePlans.iterator();
		while (iter3.hasNext()) {
			ServicePlan servicePlan = iter3.next();
			store.saveOrUpdateServicePlan(servicePlan);
		}

		return readResults;
	}
}

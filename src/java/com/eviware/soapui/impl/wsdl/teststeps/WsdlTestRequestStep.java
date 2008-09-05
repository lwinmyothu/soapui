/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.config.RequestStepConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.impl.wsdl.*;
import com.eviware.soapui.impl.wsdl.submit.transports.http.WsdlResponse;
import com.eviware.soapui.impl.wsdl.support.assertions.AssertedXPathsContainer;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext;
import com.eviware.soapui.impl.wsdl.teststeps.actions.ChangeOperationAction;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.TestAssertionRegistry.AssertableType;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.Operation;
import com.eviware.soapui.model.iface.Request.SubmitException;
import com.eviware.soapui.model.iface.Submit;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansion;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContainer;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionUtils;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionsResult;
import com.eviware.soapui.model.support.InterfaceListenerAdapter;
import com.eviware.soapui.model.support.ProjectListenerAdapter;
import com.eviware.soapui.model.support.TestStepBeanProperty;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.TestStepResult.TestStepStatus;
import com.eviware.soapui.support.resolver.ChangeOperationResolver;
import com.eviware.soapui.support.resolver.ImportInterfaceResolver;
import com.eviware.soapui.support.resolver.ResolveContext;
import com.eviware.soapui.support.types.StringToStringMap;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * WsdlTestStep that executes a WsdlTestRequest
 * 
 * @author Ole.Matzura
 */

public class WsdlTestRequestStep extends WsdlTestStepWithProperties implements OperationTestStep,
		PropertyChangeListener, PropertyExpansionContainer, Assertable
{
	private final static Logger log = Logger.getLogger(WsdlTestRequestStep.class);
	private RequestStepConfig requestStepConfig;
	private WsdlTestRequest testRequest;
	private WsdlOperation wsdlOperation;
	private final InternalProjectListener projectListener = new InternalProjectListener();
	private final InternalInterfaceListener interfaceListener = new InternalInterfaceListener();
	private WsdlSubmit<WsdlRequest> submit;

	public WsdlTestRequestStep(WsdlTestCase testCase, TestStepConfig config, boolean forLoadTest)
	{
		super(testCase, config, true, forLoadTest);

		if (getConfig().getConfig() != null)
		{
			requestStepConfig = (RequestStepConfig) getConfig().getConfig().changeType(RequestStepConfig.type);

			wsdlOperation = findWsdlOperation();
			if (wsdlOperation == null)
			{
				log.error("Could not find operation [" + requestStepConfig.getOperation() + "] in interface ["
						+ requestStepConfig.getInterface() + "] for test request [" + getName() + "] in TestCase ["
						+ getTestCase().getTestSuite().getName() + "/" + getTestCase().getName() + "]");
				requestStepConfig.setRequest(null);
				setDisabled(true);
			}
			else
			{
				if (!forLoadTest)
				{
					wsdlOperation.getInterface().getProject().addProjectListener(projectListener);
					wsdlOperation.getInterface().addInterfaceListener(interfaceListener);

					// we need to listen for name changes which happen when
					// interfaces are updated..
					wsdlOperation.getInterface().addPropertyChangeListener(this);
					wsdlOperation.addPropertyChangeListener(this);
				}

				testRequest = new WsdlTestRequest(wsdlOperation, requestStepConfig.getRequest(), this, forLoadTest);
				testRequest.addPropertyChangeListener(this);

				if (config.isSetName())
					testRequest.setName(config.getName());
				else
					config.setName(testRequest.getName());
			}
		}
		else
		{
			requestStepConfig = (RequestStepConfig) getConfig().addNewConfig().changeType(RequestStepConfig.type);
		}

		// init properties
		if (testRequest != null)
		{
			addProperty(new TestStepBeanProperty("Endpoint", false, testRequest, "endpoint", this));
			addProperty(new TestStepBeanProperty("Username", false, testRequest, "username", this));
			addProperty(new TestStepBeanProperty("Password", false, testRequest, "password", this));
			addProperty(new TestStepBeanProperty("Domain", false, testRequest, "domain", this));
			addProperty(new TestStepBeanProperty("Request", false, testRequest, "requestContent", this)
			{
				@Override
				public String getDefaultValue()
				{
					return getOperation().createRequest(true);
				}
			});
			addProperty(new TestStepBeanProperty("Response", true, testRequest, "responseContent", this)
			{
				@Override
				public String getDefaultValue()
				{
					return getOperation().createResponse(true);
				}
			});
		}
	}

	@Override
	public WsdlTestStep clone(WsdlTestCase targetTestCase, String name)
	{
		beforeSave();

		TestStepConfig config = (TestStepConfig) getConfig().copy();
		RequestStepConfig stepConfig = (RequestStepConfig) config.getConfig().changeType(RequestStepConfig.type);

		while (stepConfig.getRequest().sizeOfAttachmentArray() > 0)
			stepConfig.getRequest().removeAttachment(0);

		config.setName(name);
		stepConfig.getRequest().setName(name);

		WsdlTestRequestStep result = (WsdlTestRequestStep) targetTestCase.addTestStep(config);
		testRequest.copyAttachmentsTo(result.getTestRequest());

		return result;
	}

	private WsdlOperation findWsdlOperation()
	{
		WsdlTestCase testCase = getTestCase();
		if (testCase == null || testCase.getTestSuite() == null)
			return null;

		Project project = testCase.getTestSuite().getProject();
		WsdlOperation operation = null;
		for (int c = 0; c < project.getInterfaceCount(); c++)
		{
			if (project.getInterfaceAt(c).getName().equals(requestStepConfig.getInterface()))
			{
				WsdlInterface iface = (WsdlInterface) project.getInterfaceAt(c);
				for (int i = 0; i < iface.getOperationCount(); i++)
				{
					if (iface.getOperationAt(i).getName().equals(requestStepConfig.getOperation()))
					{
						operation = iface.getOperationAt(i);
						break;
					}
				}

				if (operation != null)
					break;
			}
		}
		return operation;
	}

	public String getInterfaceName()
	{
		return requestStepConfig.getInterface();
	}

	public String getOperationName()
	{
		return requestStepConfig.getOperation();
	}

	@Override
	public void release()
	{
		super.release();

		if (wsdlOperation == null)
			wsdlOperation = findWsdlOperation();

		if (wsdlOperation != null)
		{
			wsdlOperation.removePropertyChangeListener(this);
			wsdlOperation.getInterface().getProject().removeProjectListener(projectListener);
			wsdlOperation.getInterface().removeInterfaceListener(interfaceListener);
			wsdlOperation.getInterface().removePropertyChangeListener(this);
		}

		// could be null if initialization failed..
		if (testRequest != null)
		{
			testRequest.removePropertyChangeListener(this);
			testRequest.release();
		}
	}

	@Override
	public void resetConfigOnMove(TestStepConfig config)
	{
		super.resetConfigOnMove(config);

		requestStepConfig = (RequestStepConfig) config.getConfig().changeType(RequestStepConfig.type);
		testRequest.updateConfig(requestStepConfig.getRequest());
	}

	@Override
	public ImageIcon getIcon()
	{
		return testRequest == null ? null : testRequest.getIcon();
	}

	public WsdlTestRequest getTestRequest()
	{
		return testRequest;
	}

	@Override
	public void setName(String name)
	{
		super.setName(name);
		testRequest.setName(name);
	}

	public void propertyChange(PropertyChangeEvent arg0)
	{
		if (arg0.getSource() == wsdlOperation)
		{
			if (arg0.getPropertyName().equals(Operation.NAME_PROPERTY))
			{
				requestStepConfig.setOperation((String) arg0.getNewValue());
			}
		}
		else if (arg0.getSource() == wsdlOperation.getInterface())
		{
			if (arg0.getPropertyName().equals(Interface.NAME_PROPERTY))
			{
				requestStepConfig.setInterface((String) arg0.getNewValue());
			}
		}
		else if (arg0.getPropertyName().equals(TestAssertion.CONFIGURATION_PROPERTY)
				|| arg0.getPropertyName().equals(TestAssertion.DISABLED_PROPERTY))
		{
			if (getTestRequest().getResponse() != null)
			{
				getTestRequest().assertResponse(new WsdlTestRunContext(this));
			}
		}
		else
		{
			if (arg0.getSource() == testRequest && arg0.getPropertyName().equals(WsdlTestRequest.NAME_PROPERTY))
			{
				if (!super.getName().equals((String) arg0.getNewValue()))
					super.setName((String) arg0.getNewValue());
			}

			notifyPropertyChanged(arg0.getPropertyName(), arg0.getOldValue(), arg0.getNewValue());
		}
	}

	public TestStepResult run(TestRunner runner, TestRunContext runContext)
	{
		WsdlTestRequestStepResult testStepResult = new WsdlTestRequestStepResult(this);

		try
		{
			submit = testRequest.submit(runContext, false);
			WsdlResponse response = (WsdlResponse) submit.getResponse();

			if (submit.getStatus() != Submit.Status.CANCELED)
			{
				if (submit.getStatus() == Submit.Status.ERROR)
				{
					testStepResult.setStatus(TestStepStatus.FAILED);
					testStepResult.addMessage(submit.getError().toString());

					testRequest.setResponse(null, runContext);
				}
				else if (response == null)
				{
					testStepResult.setStatus(TestStepStatus.FAILED);
					testStepResult.addMessage("Request is missing response");

					testRequest.setResponse(null, runContext);
				}
				else
				{
					runContext.setProperty(AssertedXPathsContainer.ASSERTEDXPATHSCONTAINER_PROPERTY, testStepResult);
					testRequest.setResponse(response, runContext);

					testStepResult.setTimeTaken(response.getTimeTaken());
					testStepResult.setSize(response.getContentLength());
					testStepResult.setResponse(response);

					switch (testRequest.getAssertionStatus())
					{
					case FAILED:
						testStepResult.setStatus(TestStepStatus.FAILED);
						break;
					case VALID:
						testStepResult.setStatus(TestStepStatus.OK);
						break;
					case UNKNOWN:
						testStepResult.setStatus(TestStepStatus.UNKNOWN);
						break;
					}
				}
			}
			else
			{
				testStepResult.setStatus(TestStepStatus.CANCELED);
				testStepResult.addMessage("Request was canceled");
			}

			if (response != null)
				testStepResult.setRequestContent(response.getRequestContent());
			else
				testStepResult.setRequestContent(testRequest.getRequestContent());
		}
		catch (SubmitException e)
		{
			testStepResult.setStatus(TestStepStatus.FAILED);
			testStepResult.addMessage("SubmitException: " + e);
		}
		finally
		{
			submit = null;
		}

		testStepResult.setDomain(PropertyExpansionUtils.expandProperties(runContext, testRequest.getDomain()));
		testStepResult.setUsername(PropertyExpansionUtils.expandProperties(runContext, testRequest.getUsername()));
		testStepResult.setPassword(PropertyExpansionUtils.expandProperties(runContext, testRequest.getPassword()));
		testStepResult.setEndpoint(PropertyExpansionUtils.expandProperties(runContext, testRequest.getEndpoint()));
		testStepResult.setEncoding(PropertyExpansionUtils.expandProperties(runContext, testRequest.getEncoding()));

		if (testStepResult.getStatus() != TestStepStatus.CANCELED)
		{
			AssertionStatus assertionStatus = testRequest.getAssertionStatus();
			switch (assertionStatus)
			{
			case FAILED:
			{
				testStepResult.setStatus(TestStepStatus.FAILED);
				if (getAssertionCount() == 0)
				{
					testStepResult.addMessage("Invalid/empty response");
				}
				else
					for (int c = 0; c < getAssertionCount(); c++)
					{
						AssertionError[] errors = getAssertionAt(c).getErrors();
						if (errors != null)
						{
							for (AssertionError error : errors)
							{
								testStepResult.addMessage(error.getMessage());
							}
						}
					}

				break;
			}
				// default : testStepResult.setStatus( TestStepStatus.OK ); break;
			}
		}

		return testStepResult;
	}

	public WsdlMessageAssertion getAssertionAt(int index)
	{
		return testRequest.getAssertionAt(index);
	}

	public int getAssertionCount()
	{
		return testRequest == null ? 0 : testRequest.getAssertionCount();
	}

	public class InternalProjectListener extends ProjectListenerAdapter
	{
		@Override
		public void interfaceRemoved(Interface iface)
		{
			if (wsdlOperation != null && wsdlOperation.getInterface().equals(iface))
			{
				log.debug("Removing test step due to removed interface");
				(getTestCase()).removeTestStep(WsdlTestRequestStep.this);
			}
		}
	}

	public class InternalInterfaceListener extends InterfaceListenerAdapter
	{
		@Override
		public void operationRemoved(Operation operation)
		{
			if (operation == wsdlOperation)
			{
				log.debug("Removing test step due to removed operation");
				(getTestCase()).removeTestStep(WsdlTestRequestStep.this);
			}
		}

		@Override
		public void operationUpdated(Operation operation)
		{
			if (operation == wsdlOperation)
			{
				requestStepConfig.setOperation(operation.getName());
			}
		}
	}

	@Override
	public boolean cancel()
	{
		if (submit == null)
			return false;

		submit.cancel();

		return true;
	}

	@Override
	public Collection<WsdlInterface> getRequiredInterfaces()
	{
		ArrayList<WsdlInterface> result = new ArrayList<WsdlInterface>();
		result.add(findWsdlOperation().getInterface());
		return result;
	}

	@Override
	public boolean dependsOn(AbstractWsdlModelItem<?> modelItem)
	{
		if (modelItem instanceof Interface && testRequest.getOperation().getInterface() == modelItem)
		{
			return true;
		}
		else if (modelItem instanceof Operation && testRequest.getOperation() == modelItem)
		{
			return true;
		}

		return false;
	}

	@Override
	public void beforeSave()
	{
		super.beforeSave();

		if (testRequest != null)
			testRequest.beforeSave();
	}

	@Override
	public String getDescription()
	{
		return testRequest == null ? "<missing>" : testRequest.getDescription();
	}

	@Override
	public void setDescription(String description)
	{
		if (testRequest != null)
			testRequest.setDescription(description);
	}

	public void setOperation(WsdlOperation operation)
	{
		if (wsdlOperation == operation)
			return;

		WsdlOperation oldOperation = wsdlOperation;
		wsdlOperation = operation;
		requestStepConfig.setInterface(operation.getInterface().getName());
		requestStepConfig.setOperation(operation.getName());

		oldOperation.removePropertyChangeListener(this);
		wsdlOperation.addPropertyChangeListener(this);

		testRequest.setOperation(wsdlOperation);
	}

	@Override
	public List<? extends ModelItem> getChildren()
	{
		return testRequest == null ? new ArrayList<TestAssertion>() : testRequest.getAssertionList();
	}

	public PropertyExpansion[] getPropertyExpansions()
	{
		PropertyExpansionsResult result = new PropertyExpansionsResult(this, testRequest);

		result.extractAndAddAll("requestContent");
		result.extractAndAddAll("endpoint");
		result.extractAndAddAll("username");
		result.extractAndAddAll("password");
		result.extractAndAddAll("domain");

		StringToStringMap requestHeaders = testRequest.getRequestHeaders();
		for (String key : requestHeaders.keySet())
		{
			result.extractAndAddAll(new RequestHeaderHolder(requestHeaders, key), "value");
		}

		// result.addAll( testRequest.getWssContainer().getPropertyExpansions() );

		return result.toArray(new PropertyExpansion[result.size()]);
	}

	public class RequestHeaderHolder
	{
		private final StringToStringMap valueMap;
		private final String key;

		public RequestHeaderHolder(StringToStringMap valueMap, String key)
		{
			this.valueMap = valueMap;
			this.key = key;
		}

		public String getValue()
		{
			return valueMap.get(key);
		}

		public void setValue(String value)
		{
			valueMap.put(key, value);
			testRequest.setRequestHeaders(valueMap);
		}
	}

	public TestAssertion addAssertion(String type)
	{
		WsdlMessageAssertion result = testRequest.addAssertion(type);
		return result;
	}

	public void addAssertionsListener(AssertionsListener listener)
	{
		testRequest.addAssertionsListener(listener);
	}

	public TestAssertion cloneAssertion(TestAssertion source, String name)
	{
		return testRequest.cloneAssertion(source, name);
	}

	public String getAssertableContent()
	{
		return testRequest.getAssertableContent();
	}

	public AssertableType getAssertableType()
	{
		return testRequest.getAssertableType();
	}

	public TestAssertion getAssertionByName(String name)
	{
		return testRequest.getAssertionByName(name);
	}

	public List<TestAssertion> getAssertionList()
	{
		return testRequest.getAssertionList();
	}

	public AssertionStatus getAssertionStatus()
	{
		return testRequest.getAssertionStatus();
	}

	public Interface getInterface()
	{
		return getOperation().getInterface();
	}

	public WsdlOperation getOperation()
	{
		return wsdlOperation;
	}

	public TestStep getTestStep()
	{
		return this;
	}

	public void removeAssertion(TestAssertion assertion)
	{
		testRequest.removeAssertion(assertion);
	}

	public void removeAssertionsListener(AssertionsListener listener)
	{
		testRequest.removeAssertionsListener(listener);
	}

	public Map<String, TestAssertion> getAssertions()
	{
		return testRequest.getAssertions();
	}

	@Override
	public void prepare(TestRunner testRunner, TestRunContext testRunContext) throws Exception
	{
		super.prepare(testRunner, testRunContext);

		for (TestAssertion assertion : testRequest.getAssertionList())
		{
			assertion.prepare(testRunner, testRunContext);
		}
	}

	public String getDefaultAssertableContent()
	{
		return testRequest.getDefaultAssertableContent();
	}

	public void resolve(ResolveContext context)
	{
		super.resolve(context);

		if (wsdlOperation == null)
		{
			context.addPathToResolve(this, "Missing SOAP Operation in Project",
					requestStepConfig.getInterface() + "/" + requestStepConfig.getOperation(),
					new TestRequestDefaultResolveAction()).addResolvers(new ImportInterfaceResolver(this),
					new ChangeOperationResolver(this));
		}
		else
		{
			testRequest.resolve(context);
		}
	}
}

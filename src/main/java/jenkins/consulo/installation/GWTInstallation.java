/*
 * Copyright 2013-2016 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.consulo.installation;

import java.io.File;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import hudson.Extension;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * @author VISTALL
 * @since 13.08.2016
 */
public class GWTInstallation extends ToolInstallation
{
	@Extension
	public static class DescriptorImpl extends ToolDescriptor<GWTInstallation>
	{
		@Override
		public String getDisplayName()
		{
			return "GWT";
		}

		@Override
		public FormValidation doCheckHome(@QueryParameter File value)
		{
			if(!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER))
			{
				return FormValidation.ok();
			}

			if(value.getPath().equals(""))
			{
				return FormValidation.ok();
			}

			if(!value.isDirectory())
			{
				return FormValidation.error("Not GWT Directory");
			}

			File gwtUserJar = new File(value, "gwt-user.jar");
			if(!gwtUserJar.exists())
			{
				return FormValidation.error("Not GWT Directory");
			}

			return FormValidation.ok();
		}
	}

	@DataBoundConstructor
	public GWTInstallation(String name, String home, List<? extends ToolProperty<?>> properties)
	{
		super(name, home, properties);
	}
}

/*
 * Copyright 2013-2021 must-be.org
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

package jenkins.consulo.postBuild;

import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author VISTALL
 * @since 07/09/2021
 */
public abstract class DeployDescriptorBase extends BuildStepDescriptor<Publisher>
{
	private String oauthKey;

	private String jenkinsPassword;

	public DeployDescriptorBase()
	{
		load();
	}

	public String getOauthKey()
	{
		return oauthKey;
	}

	public String getJenkinsPassword()
	{
		return jenkinsPassword;
	}

	public void setOauthKey(String oauthKey, String jenkinsPassword)
	{
		this.oauthKey = oauthKey;
		this.jenkinsPassword = jenkinsPassword;
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject json) throws FormException
	{
		oauthKey = json.getJSONObject("consulo").getString("oauthKey");

		jenkinsPassword = json.getJSONObject("consulo").getString("jenkinsPassword");

		save();

		return true;
	}

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> jobType)
	{
		return true;
	}

	@SuppressWarnings("unused") //used by jenkins
	public ListBoxModel doFillPluginChannelItems()
	{
		ListBoxModel items = new ListBoxModel();
		for(PluginChannel goal : PluginChannel.values())
		{
			items.add(goal.name(), goal.name());
		}
		return items;
	}
}
package org.mustbe.consulo.jenkins.build;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

/**
 * @author VISTALL
 * @since 17.06.2015
 */
public class ConsuloBuilder extends Builder
{
	@Extension
	public static final DescriptorImpl INSTANCE = new DescriptorImpl();

	public static class DescriptorImpl extends BuildStepDescriptor<Builder>
	{
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType)
		{
			return true;
		}

		@Override
		public String getDisplayName()
		{
			return "Invoke consulo project build";
		}

		@Override
		public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			return new ConsuloBuilder();
		}
	}
}

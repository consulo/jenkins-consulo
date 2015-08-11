package jenkins.consulo.build;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
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

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		return super.perform(build, launcher, listener);
	}
}

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
 * @since 11.08.2015
 */
public class ConsuloInstallation extends ToolInstallation
{
	@Extension
	public static class DescriptorImpl extends ToolDescriptor<ConsuloInstallation>
	{
		@Override
		public String getDisplayName()
		{
			return "Consulo";
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
				return FormValidation.error("Not Consulo Directory");
			}

			File ideaJar = new File(value, "lib/idea.jar");
			if(!ideaJar.exists())
			{
				return FormValidation.error("Not Consulo Directory");
			}

			return FormValidation.ok();
		}
	}

	@DataBoundConstructor
	public ConsuloInstallation(String name, String home, List<? extends ToolProperty<?>> properties)
	{
		super(name, home, properties);
	}
}

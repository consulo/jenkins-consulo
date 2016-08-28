package jenkins.consulo.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.JDK;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

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
			return "Invoke cold";
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
		FilePath workspace = build.getWorkspace();

		FilePath outPath = workspace.child("out");
		if(outPath.exists())
		{
			outPath.deleteRecursive();
		}

		Jenkins jenkins = Jenkins.getInstance();

		JDK jdk = jenkins.getJDK("1.8");
		if(jdk == null)
		{
			listener.error("'1.8' jdk required");
			return false;
		}
		URL coldRunner = new URL("https://github.com/consulo/cold/raw/master/build/cold.jar");
		URLConnection urlConnection = coldRunner.openConnection();

		FilePath coldJar = workspace.createTempFile("cold", ".jar");

		try (InputStream inputStream = urlConnection.getInputStream())
		{
			try (OutputStream write = coldJar.write())
			{
				IOUtils.copy(inputStream, write);
			}
		}

		Launcher.ProcStarter procStarter = launcher.launch();
		File java = new File(jdk.getBinDir(), launcher.isUnix() ? "java" : "java.exe");

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(java);

		int i = 0;
		for(JDK temp : jenkins.getJDKs())
		{
			String value = "JDK" + ";" + temp.getName() + ";" + temp.getHome();
			args.addKeyValuePair(null, "cold.sdk." + (i ++), value, false);
		}

		args.add("-jar");
		args.add(coldJar.getName());

		procStarter.cmds(args);
		procStarter.pwd(workspace);
		procStarter.stdout(listener);

		Proc launch = launcher.launch(procStarter);
		int join = launch.join();
		coldJar.delete();
		return join == 0;
	}
}

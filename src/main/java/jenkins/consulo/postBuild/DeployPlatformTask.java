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

package jenkins.consulo.postBuild;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;

/**
 * @author VISTALL
 * @since 29-Aug-16
 */
public class DeployPlatformTask extends Notifier
{
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		public DescriptorImpl()
		{
			load();
		}

		public String getDisplayName()
		{
			return "Deploy platform artifacts to repository (Consulo)";
		}

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

	private final boolean enableRepositoryUrl;
	private final boolean allowUnstable;
	private final String repositoryUrl;
	private final String pluginChannel;

	private static final Collection<String> ourAllowedArtifacts = Arrays.asList("consulo-win-no-jre.tar.gz", "consulo-win.tar.gz", "consulo-win64.tar.gz", "consulo-linux-no-jre.tar.gz",
			"consulo-linux.tar.gz", "consulo-linux64.tar.gz", "consulo-mac-no-jre.tar.gz", "consulo-mac64.tar.gz");

	@DataBoundConstructor
	public DeployPlatformTask(String repositoryUrl, boolean enableRepositoryUrl, String pluginChannel, boolean allowUnstable)
	{
		this.repositoryUrl = repositoryUrl;
		this.enableRepositoryUrl = enableRepositoryUrl;
		this.pluginChannel = pluginChannel;
		this.allowUnstable = allowUnstable;
	}

	public boolean isAllowUnstable()
	{
		return allowUnstable;
	}

	public boolean isEnableRepositoryUrl()
	{
		return enableRepositoryUrl;
	}

	public String getRepositoryUrl()
	{
		return repositoryUrl;
	}

	public String getPluginChannel()
	{
		return pluginChannel;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		Result result = build.getResult();
		if(result == null || (allowUnstable ? result.isWorseThan(Result.UNSTABLE) : result.isWorseOrEqualTo(Result.UNSTABLE)))
		{
			throw new IOException("Project is not build");
		}

		ArtifactPaths artifactPaths = ArtifactPaths.find(build);

		String deployKey = loadDeployKey();

		FilePath workspace = build.getWorkspace();
		FilePath allArtifactsDir = workspace.child(artifactPaths.getAllArtifactsPath());
		if(!allArtifactsDir.exists())
		{
			throw new IOException("No artifacts");
		}

		String repoUrl = enableRepositoryUrl ? repositoryUrl : Urls.ourDefaultRepositoryUrl;

		int artifactCount = 0;
		for(FilePath artifactPath : allArtifactsDir.list())
		{
			if(!ourAllowedArtifacts.contains(artifactPath.getName()))
			{
				continue;
			}

			PostMethod postMethod = new PostMethod(repoUrl + "platformDeploy?channel=" + pluginChannel + "&platformVersion=" + build.getNumber());
			if(deployKey != null)
			{
				postMethod.setRequestHeader("Authorization", deployKey);
			}

			InputStream inputStream = artifactPath.read();
			Part[] parts = {
					new FilePart("file", new ByteArrayPartSource(artifactPath.getName(), IOUtils.toByteArray(inputStream))),
			};
			IOUtils.closeQuietly(inputStream);

			MultipartRequestEntity entity = new MultipartRequestEntity(parts, postMethod.getParams());
			postMethod.setRequestEntity(entity);

			HttpClient client = new HttpClient();
			client.getParams().setSoTimeout(5 * 60000);

			int i = client.executeMethod(postMethod);
			if(i != HttpServletResponse.SC_OK)
			{
				throw new IOException("Failed to deploy artifact " + artifactPath.getName() + ", Status Code: " + i + ", Status Text: " + postMethod.getStatusText());
			}
			listener.getLogger().println("Deployed artifact: " + artifactPath.getName());

			artifactCount++;
		}

		if(artifactCount == 0)
		{
			throw new IOException("No artifacts for deploy");
		}
		return true;
	}

	private static String loadDeployKey()
	{
		try
		{
			File file = new File(System.getProperty("user.home"), ".consuloWebservice/deploy.key");

			return file.exists() ? FileUtils.readFileToString(file, "UTF-8") : null;
		}
		catch(Exception e)
		{
			return null;
		}
	}
}

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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author VISTALL
 * @since 29-Aug-16
 */
public class DeployPluginTask extends Notifier
{
	@Extension
	public static class DescriptorImpl extends DeployDescriptorBase
	{
		@Override
		public String getDisplayName()
		{
			return "Deploy plugin artifacts to repository (Consulo)";
		}
	}

	private final boolean enableRepositoryUrl;
	private final boolean allowUnstable;
	private final String repositoryUrl;
	private final String pluginChannel;

	@DataBoundConstructor
	public DeployPluginTask(String repositoryUrl, boolean enableRepositoryUrl, String pluginChannel, boolean allowUnstable)
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

		String deployKey = ((DeployDescriptorBase) getDescriptor()).getOauthKey();
		String repoUrl = enableRepositoryUrl ? repositoryUrl : Urls.ourDefaultRepositoryUrl;
		int artifactCount = 0;

		FilePath workspace = build.getWorkspace();
		if(workspace == null)
		{
			throw new IOException("Workspace is null");
		}

		AbstractProject<?, ?> project = build.getProject();
		if(project instanceof MavenModuleSet)
		{
			listener.getLogger().println("Deploying mavenize plugins");

			Collection<MavenModule> modules = ((MavenModuleSet) project).getModules();
			for(MavenModule module : modules)
			{
				if(module.isDisabled())
				{
					continue;
				}

				String packaging = module.getPackaging();

				if("consulo-plugin".equals(packaging))
				{
					FilePath targetDirectory = workspace.child(module.getRelativePath().isEmpty() ? "target" : (module.getRelativePath() + "/target"));
					if(targetDirectory.exists())
					{
						for(FilePath artifactPath : targetDirectory.list("*.consulo-plugin"))
						{
							artifactCount += deployPlugin(repoUrl, pluginChannel, deployKey, artifactPath, listener, artifactCount);
						}
					}
				}
			}
		}
		else
		{
			listener.getLogger().println("Deploying plugins with obsolete project structure");

			FilePath allArtifactsDir = workspace.child("out/artifacts/dist");
			if(!allArtifactsDir.exists())
			{
				throw new IOException("No artifacts");
			}

			for(FilePath artifactPath : allArtifactsDir.list("*.zip"))
			{
				artifactCount += deployPlugin(repoUrl, pluginChannel, deployKey, artifactPath, listener, artifactCount);
			}
		}

		if(artifactCount == 0)
		{
			throw new IOException("No artifacts for deploy");
		}
		return true;
	}

	private static int deployPlugin(String repoUrl, String pluginChannel, String deployKey, FilePath artifactPath, BuildListener listener, int artifactCount) throws IOException, InterruptedException
	{
		PostMethod postMethod = new PostMethod(repoUrl + "pluginDeploy?channel=" + pluginChannel);
		if(deployKey != null)
		{
			postMethod.setRequestHeader("Authorization", "Bearer " + deployKey);
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
		return artifactCount;
	}
}

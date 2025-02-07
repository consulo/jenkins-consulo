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

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.gson.Gson;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.User;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.Revision;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author VISTALL
 * @since 17/10/2021
 */
public abstract class DeployArtifactTaskBase extends Notifier
{
	protected final boolean enableRepositoryUrl;
	protected final boolean allowUnstable;
	protected final String repositoryUrl;
	protected final String pluginChannel;

	protected DeployArtifactTaskBase(String repositoryUrl, boolean enableRepositoryUrl, String pluginChannel, boolean allowUnstable)
	{
		this.repositoryUrl = repositoryUrl;
		this.enableRepositoryUrl = enableRepositoryUrl;
		this.pluginChannel = pluginChannel;
		this.allowUnstable = allowUnstable;
	}

	protected int deployArtifact(String urlSuffix,
								 Map<String, String> parameters,
								 FilePath artifactPath,
								 BuildListener listener,
								 AbstractBuild<?, ?> build,
								 int artifactCount) throws IOException, InterruptedException
	{
		listener.getLogger().println("Deploying artifact: " + artifactPath.getRemote());

		String deployKey = ((DeployDescriptorBase) getDescriptor()).getOauthKey().getPlainText();
		String jenkinsPassword = ((DeployDescriptorBase) getDescriptor()).getJenkinsPassword().getPlainText();

		String repoUrl = enableRepositoryUrl ? repositoryUrl : "http://hub-backend:22333/api/repository/";

		StringBuilder builder = new StringBuilder(repoUrl);
		if(!repoUrl.endsWith("/"))
		{
			builder.append("/");
		}
		builder.append(urlSuffix).append("?");

		Map<String, String> map = new HashMap<>(parameters);
		map.put("channel", pluginChannel);

		Map.Entry[] entries = map.entrySet().toArray(new Map.Entry[0]);

		for(int i = 0; i < entries.length; i++)
		{
			if(i != 0)
			{
				builder.append("&");
			}

			Map.Entry entry = entries[i];

			builder.append(entry.getKey());
			builder.append("=");
			builder.append(entry.getValue());
		}

		GithubInfo githubInfo = null;
		GithubProjectProperty property = build.getProject().getProperty(GithubProjectProperty.class);
		if(property != null)
		{
			String projectUrl = property.getProjectUrlStr();
			String commitSha1 = null;

			BuildData action = build.getAction(BuildData.class);
			if(action != null)
			{
				Revision lastBuiltRevision = action.getLastBuiltRevision();
				if(lastBuiltRevision != null)
				{
					commitSha1 = lastBuiltRevision.getSha1String();
				}
			}

			if(projectUrl != null && commitSha1 != null)
			{
				githubInfo = new GithubInfo(projectUrl, commitSha1);
			}
		}

		List<PluginHistoryEntry> pluginHistoryEntries = buildChangeSet(build);
		Gson gson = new Gson();

		InputStream contentStream;
		try (CloseableHttpClient client = HttpClients.createMinimal())
		{
			HttpPost request = new HttpPost(builder.toString());
			if(!StringUtils.isBlank(jenkinsPassword))
			{
				String serviceAccount = "jenkins@consulo.io";
				String basicAuth = serviceAccount + ":" + jenkinsPassword;
				request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8)));
			}
			else
			{
				request.addHeader("Authorization", "Bearer " + deployKey);
			}

			MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
			entityBuilder.addBinaryBody("file", contentStream = artifactPath.read(), ContentType.DEFAULT_BINARY, artifactPath.getName());
			if(!pluginHistoryEntries.isEmpty())
			{
				String historyJson = gson.toJson(pluginHistoryEntries);
				entityBuilder.addBinaryBody("history", historyJson.getBytes(StandardCharsets.UTF_8), ContentType.DEFAULT_BINARY, "history");
			}

			if(githubInfo != null)
			{
				entityBuilder.addBinaryBody("github", gson.toJson(githubInfo).getBytes(StandardCharsets.UTF_8), ContentType.DEFAULT_BINARY, "history");
			}

			request.setEntity(entityBuilder.build());

			client.execute(request, r ->
			{
				StatusLine line = r.getStatusLine();
				int statusCode = line.getStatusCode();

				if(statusCode != HttpServletResponse.SC_OK)
				{
					throw new IOException("Failed to deploy artifact " + artifactPath.getRemote() + ", Status Code: " + statusCode + ", Status Text: " + line.getReasonPhrase());
				}

				listener.getLogger().println("Deployed artifact: " + artifactPath.getRemote());
				return null;
			});
		}

		IOUtils.closeQuietly(contentStream);

		artifactCount++;
		return artifactCount;
	}

	public List<PluginHistoryEntry> buildChangeSet(AbstractBuild<?, ?> build)
	{
		List<PluginHistoryEntry> result = new ArrayList<>();

		SCM scm = build.getProject().getScm();
		RepositoryBrowser browser = scm.getEffectiveBrowser();
		List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = build.getChangeSets();
		for(ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : changeSets)
		{
			String scmUrl = getRepoUrl(browser);

			for(ChangeLogSet.Entry entry : changeSet)
			{
				PluginHistoryEntry pluginHistoryEntry = new PluginHistoryEntry();

				pluginHistoryEntry.repoUrl = scmUrl;
				pluginHistoryEntry.commitMessage = entry.getMsg();
				pluginHistoryEntry.commitHash = entry.getCommitId();
				pluginHistoryEntry.commitTimestamp = entry.getTimestamp();

				User author = entry.getAuthor();
				if(author != User.getUnknown())
				{
					String displayName = author.getDisplayName();
					String fullName = author.getFullName();

					pluginHistoryEntry.commitAuthor = fullName + "<" + displayName + ">";
				}

				updateIfGitToAuthorInfo((GitChangeSet) entry, pluginHistoryEntry);

				//URL changeSetLink = browser == null ? null : browser.getChangeSetLink(entry);
				result.add(pluginHistoryEntry);
			}
		}

		return result;
	}

	private static void updateIfGitToAuthorInfo(GitChangeSet entry, PluginHistoryEntry pluginHistoryEntry)
	{
		String author = entry.getAuthorName();
		String authorEmail = entry.getAuthorEmail();

		if(author != null && authorEmail != null)
		{
			pluginHistoryEntry.commitAuthor = author + " <" + authorEmail + ">";
		}
	}

	private static String getRepoUrl(RepositoryBrowser repositoryBrowser)
	{
		GitRepositoryBrowser browser = (GitRepositoryBrowser) repositoryBrowser;
		return browser.getRepoUrl();
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
}

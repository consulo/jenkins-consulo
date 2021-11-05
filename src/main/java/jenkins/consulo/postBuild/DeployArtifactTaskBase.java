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

import com.google.gson.Gson;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	protected int deployArtifact(String urlSuffix, Map<String, String> parameters, FilePath artifactPath, BuildListener listener, AbstractBuild<?, ?> build, int artifactCount) throws IOException, InterruptedException
	{
		String deployKey = ((DeployDescriptorBase) getDescriptor()).getOauthKey();
		String repoUrl = enableRepositoryUrl ? repositoryUrl : "https://api.consulo.io/repository/";

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

		PostMethod postMethod = new PostMethod(builder.toString());
		postMethod.addRequestHeader("Authorization", "Bearer " + deployKey);

		List<PluginHistoryEntry> pluginHistoryEntries = buildChangeSet(build);

		String historyJson = new Gson().toJson(pluginHistoryEntries);

		InputStream inputStream = artifactPath.read();
		Part[] parts = {
				new FilePart("file", new ByteArrayPartSource(artifactPath.getName(), IOUtils.toByteArray(inputStream))),
				new FilePart("history", new ByteArrayPartSource("history.json", historyJson.getBytes(StandardCharsets.UTF_8)))
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

				updateIfGitToAuthorInfo(entry, pluginHistoryEntry);

				//URL changeSetLink = browser == null ? null : browser.getChangeSetLink(entry);
				result.add(pluginHistoryEntry);
			}
		}

		return result;
	}

	private static void updateIfGitToAuthorInfo(ChangeLogSet.Entry entry, PluginHistoryEntry pluginHistoryEntry)
	{
		String author = invokeField(entry, "author", String.class);
		String authorEmail = invokeField(entry, "authorEmail", String.class);
		//String authorTime = invokeField(entry, "authorTime", String.class);

		if(author != null && authorEmail != null)
		{
			pluginHistoryEntry.commitAuthor = author + " <" + authorEmail + ">";
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invokeField(Object o, String fieldName, Class<T> fieldType)
	{
		try
		{
			Field declaredField = o.getClass().getDeclaredField(fieldName);
			if(declaredField.getType() != fieldType)
			{
				return null;
			}
			declaredField.setAccessible(true);
			return (T) declaredField.get(o);
		}
		catch(Throwable ignored)
		{
		}
		return null;
	}

	private static String getRepoUrl(RepositoryBrowser repositoryBrowser)
	{
		try
		{
			MethodHandle getRepoUrl = MethodHandles.lookup().findVirtual(repositoryBrowser.getClass(), "getRepoUrl", MethodType.methodType(String.class));
			return (String) getRepoUrl.invoke(repositoryBrowser);
		}
		catch(Throwable ignored)
		{
		}
		return null;
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

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.sf.json.JSONObject;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;

/**
 * @author VISTALL
 * @since 13-Aug-16
 */
public class ZipPostTask extends Recorder implements SimpleBuildStep
{
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		public DescriptorImpl()
		{
		}

		public String getDisplayName()
		{
			return "Create plugin artifacts (Consulo)";
		}

		@Override
		public ZipPostTask newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			return new ZipPostTask();
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType)
		{
			return true;
		}
	}

	public ZipPostTask()
	{
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	@Override
	public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException
	{
		FilePath distPath = workspace.child("out/artifacts/dist");
		if(!distPath.exists())
		{
			listener.error("'out/artifacts/dist' is not exists");
			return;
		}

		List<FilePath> filePaths = distPath.listDirectories();
		for(FilePath pluginPath : filePaths)
		{
			// pair ID + NAME
			Map.Entry<String, String> pluginInfo = new AbstractMap.SimpleImmutableEntry<>(null, null);

			FilePath[] libs = pluginPath.list("lib/*.jar");
			mainLoop:
			for(FilePath someJar : libs)
			{
				ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(someJar.read());

				ArchiveEntry entry = null;
				while((entry = zipArchiveInputStream.getNextEntry()) != null)
				{
					String name = entry.getName();
					if(name.equals("META-INF/plugin.xml"))
					{
						byte[] data = IOUtils.toByteArray(zipArchiveInputStream);

						Map.Entry<String, String> temp = findPluginId(new ByteArrayInputStream(data));
						if(temp != null)
						{
							pluginInfo = temp;
						}

						break mainLoop;
					}
				}
				zipArchiveInputStream.close();
			}


			if(pluginInfo.getKey() == null && pluginInfo.getValue() == null)
			{
				throw new IOException("Path " + pluginPath + " is not plugin");
			}

			if(pluginInfo.getKey() == null)
			{
				throw new IOException("Plugin with name: " + pluginInfo.getValue() + " don't have pluginId");
			}

			if(pluginInfo.getValue() == null)
			{
				throw new IOException("Plugin with id: " + pluginInfo.getKey() + " don't have pluginName");
			}

			if(!pluginInfo.getKey().equals(pluginPath.getName()))
			{
				throw new IOException(String.format("Plugin dir(%s) is not equal pluginId(%s)", pluginPath.getName(), pluginInfo.getKey()));
			}

			pluginPath.zip(distPath.child(pluginInfo.getKey() + "_" + run.getId() + ".zip"));
		}
	}

	private static Map.Entry<String, String> findPluginId(InputStream inputStream) throws IOException
	{
		String id = null;
		String name = null;

		try
		{
			SAXReader reader = new SAXReader();
			Document document = reader.read(inputStream);

			Element rootElement = document.getRootElement();
			Element temp = rootElement.element("id");
			if(temp != null)
			{
				id = temp.getStringValue();
			}
			temp = rootElement.element("name");
			if(temp != null)
			{
				name = temp.getStringValue();
			}
		}
		catch(DocumentException e)
		{
			throw new IOException(e);
		}

		return new AbstractMap.SimpleImmutableEntry<>(id, name);
	}
}

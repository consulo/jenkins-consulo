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

package jenkins.consulo.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.DescribableList;
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

		Map<String, String> tools = findAllTools(build);

		String jdk = tools.get("1.8");
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
		File java = new File(new File(jdk, "bin"), launcher.isUnix() ? "java" : "java.exe");

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(java);

		int i = 0;
		for(Map.Entry<String, String> entry : tools.entrySet())
		{
			String value = "JDK" + ";" + entry.getKey() + ";" + entry.getValue();
			args.addKeyValuePair(null, "cold.sdk." + (i++), value, false);
		}
		args.addKeyValuePair(null, "cold.build.number", String.valueOf(build.getNumber()), false);

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

	private static Map<String, String> findAllTools(AbstractBuild<?, ?> build)
	{
		Map<String, String> map = new HashMap<>();
		Node currentNode = build.getBuiltOn();
		DescribableList<NodeProperty<?>, NodePropertyDescriptor> properties = currentNode.getNodeProperties();
		ToolLocationNodeProperty toolLocation = properties.get(ToolLocationNodeProperty.class);
		if(toolLocation != null)
		{
			List<ToolLocationNodeProperty.ToolLocation> locations = toolLocation.getLocations();
			for(ToolLocationNodeProperty.ToolLocation location : locations)
			{
				if(StringUtils.isNotBlank(location.getHome()))
				{
					map.put(location.getName(), location.getHome());
				}
			}
		}

		if(currentNode instanceof Jenkins)
		{
			List<JDK> jdks = ((Jenkins) currentNode).getJDKs();
			for(JDK jdk : jdks)
			{
				map.put(jdk.getName(), jdk.getHome());
			}
		}
		return map;
	}
}

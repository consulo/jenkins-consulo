package jenkins.consulo.postBuild;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
			return "Create artifacts from dist (Consulo)";
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
		return BuildStepMonitor.NONE;
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
		for(FilePath someArtifact : filePaths)
		{
			String artifactName = someArtifact.getName();
			FilePath[] libs = someArtifact.list("lib/*.jar");
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

						String pluginId = findPluginId(new ByteArrayInputStream(data));
						if(pluginId != null)
						{
							artifactName = pluginId;
						}

						break mainLoop;
					}
				}
				zipArchiveInputStream.close();
			}


			someArtifact.zip(distPath.child(artifactName + "_" + run.getId() + ".zip"));
		}
	}

	private static String findPluginId(InputStream inputStream) throws IOException
	{
		try
		{
			SAXReader reader = new SAXReader();
			Document document = reader.read(inputStream);

			Element rootElement = document.getRootElement();
			Element temp = rootElement.element("id");
			if(temp != null)
			{
				return temp.getStringValue();
			}
			temp = rootElement.element("name");
			if(temp != null)
			{
				return temp.getStringValue();
			}
		}
		catch(DocumentException e)
		{
			throw new IOException(e);
		}

		return null;
	}
}

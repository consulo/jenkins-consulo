/*
 * Copyright 2013-2019 must-be.org
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

package jenkins.consulo.postBuild.consuloArtifactTask;

import com.Ostermiller.util.BinaryDataException;
import com.Ostermiller.util.LineEnds;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import jakarta.annotation.Nullable;
import jenkins.consulo.postBuild.consuloArtifactTask.jre.ArchiveEntryWrapper;
import jenkins.consulo.postBuild.consuloArtifactTask.jre.ArchivedBundledJRE;
import jenkins.consulo.postBuild.consuloArtifactTask.jre.BundledJRE;
import jenkins.consulo.postBuild.consuloArtifactTask.jre.LocalBundledJRE;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * @author VISTALL
 * @since 2019-11-17
 */
public class Generator
{
	private static final String[] ourExecutable = new String[]{
			// linux
			"Consulo/consulo.sh",
			"Consulo/platform/buildSNAPSHOT/bin/launcher.sh",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier64",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier-aarch64",
			// new linux
			"Consulo/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier",
			"Consulo/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier64",
			"Consulo/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier-aarch64",
			"Consulo/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier-loong64",
			"Consulo/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier-riscv64",

			// mac
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/fsnotifier-aarch64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/restarter-aarch64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv.py",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv-aarch64",
			"Consulo.app/Contents/MacOS/consulo",

			// new mac
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/fsnotifier-aarch64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/printenv",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/printenv64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo/native/printenv-aarch64",

			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo.desktop.awt/native/restarter",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo.desktop.awt/native/restarter64",
			"Consulo.app/Contents/platform/buildSNAPSHOT/modules/consulo.desktop.awt/native/restarter-aarch64",
	};

	public static final String ourBuildSNAPSHOT = "buildSNAPSHOT";

	protected FilePath myDistPath;
	protected FilePath myTargetDir;
	protected FilePath myJreDirectory;
	protected int myBuildNumber;
	protected BuildListener myListener;

	public Generator(FilePath distPath, FilePath targetDir, FilePath jreDirectory, int buildNumber, BuildListener listener)
	{
		myDistPath = distPath;
		myTargetDir = targetDir;
		myJreDirectory = jreDirectory;
		myBuildNumber = buildNumber;
		myListener = listener;
	}

	private FilePath prepareJre(@Nullable String jdkArchivePathOrUrl) throws Exception
	{
		if(jdkArchivePathOrUrl != null && jdkArchivePathOrUrl.startsWith("https://"))
		{
			myListener.getLogger().println("JRE: downloading " + jdkArchivePathOrUrl);

			URL jreUrl = new URL(jdkArchivePathOrUrl);

			HttpURLConnection connection = (HttpURLConnection) jreUrl.openConnection();

			String fileName = null;
			String contentDisposition = connection.getHeaderField("Content-Disposition");
			if(contentDisposition != null)
			{
				fileName = contentDisposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
			}
			else
			{
				fileName = jdkArchivePathOrUrl.substring(jdkArchivePathOrUrl.lastIndexOf('/') + 1, jdkArchivePathOrUrl.length());
			}

			connection.disconnect();

			FilePath jreFileArchiveFile = myJreDirectory.child(fileName);

			jreFileArchiveFile.copyFrom(jreUrl);

			return jreFileArchiveFile;
		}
		else
		{
			return jdkArchivePathOrUrl == null ? null : new FilePath(new File(jdkArchivePathOrUrl));
		}
	}

	public void buildWindowsInstaller(FilePath workspace, String artifactName, @Nullable String jdkArchivePathOrUrl, String nsisPath, String artifactId) throws Exception
	{
		FilePath jdkArchivePath = prepareJre(jdkArchivePathOrUrl);

		myListener.getLogger().println("Build: " + artifactId);

		FilePath nsisDistroPath = myTargetDir.child(artifactId);
		nsisDistroPath.mkdirs();

		FilePath fileZip = getOrCreateZip(artifactName);

		FilePath nsisWorkspaceDir = workspace.child(nsisPath);

		nsisWorkspaceDir.copyRecursiveTo(nsisDistroPath);

		fileZip.unzip(nsisDistroPath);

		LocalBundledJRE bundledJRE = new LocalBundledJRE(myBuildNumber, new ArchiveStreamFactory(), jdkArchivePath, false, nsisDistroPath);
		bundledJRE.build();

		FilePath[] nsisScripts = nsisDistroPath.list("*.nsi");
		if(nsisScripts.length != 1)
		{
			throw new IllegalArgumentException("Required NSIS script");
		}

		Launcher launcher = nsisDistroPath.createLauncher(myListener);
		Launcher.ProcStarter launch = launcher.launch();
		launch.cmds("makensis", nsisScripts[0].getName());
		launch.pwd(nsisDistroPath);
		launch.stdout(myListener);
		int exitCode = launch.join();
		if(exitCode != 0)
		{
			throw new IllegalArgumentException("Failed to create installer");
		}

		String fileName = artifactId + ".exe";

		FilePath exeFile = nsisDistroPath.child(fileName);
		// now we move exe file to parent dir
		exeFile.copyTo(myTargetDir.child(fileName));
		// remove this temp dir
		nsisDistroPath.deleteRecursive();
	}

	private FilePath getOrCreateZip(String artifactName) throws Exception
	{
		String zipArtifactName = artifactName + ".zip";

		FilePath fileZip = myDistPath.child(zipArtifactName);
		if(!fileZip.exists())
		{
			FilePath artifactDir = myDistPath.child(artifactName);
			if(!artifactDir.exists())
			{
				throw new IllegalArgumentException(artifactDir + " not exists");
			}

			boolean mac = artifactName.contains("-mac-");

			String childDir = mac ? "Consulo.app" : "Consulo";

			FilePath targetDir = artifactDir.child(childDir);

			// make zip archive for processing - legacy processing from zip
			targetDir.zip(fileZip);
		}

		return fileZip;
	}

	public void buildDistributionInArchive(String artifactName, @Nullable String jdkArchivePathOrUrl, String path, String archiveOutType) throws Exception
	{
		FilePath jdkArchivePath = prepareJre(jdkArchivePathOrUrl);

		myListener.getLogger().println("Build: " + path);

		ArchiveStreamFactory factory = new ArchiveStreamFactory();

		FilePath fileZip = getOrCreateZip(artifactName);

		final List<String> executables = Arrays.asList(ourExecutable);

		try (OutputStream pathStream = createOutputStream(archiveOutType, path))
		{
			ArchiveOutputStream archiveOutputStream = factory.createArchiveOutputStream(archiveOutType, pathStream);
			if(archiveOutputStream instanceof TarArchiveOutputStream)
			{
				((TarArchiveOutputStream) archiveOutputStream).setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
			}

			// move Consulo to archive, and change permissions
			try (InputStream is = fileZip.read())
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is))
				{
					ArchiveEntry tempEntry = ais.getNextEntry();
					while(tempEntry != null)
					{
						final ArchiveEntryWrapper<? extends ArchiveEntry> newEntry = createEntry(archiveOutType, tempEntry.getName(), tempEntry);

						newEntry.setMode(extractMode(tempEntry));
						newEntry.setTime(tempEntry.getLastModifiedDate().getTime());

						if(executables.contains(tempEntry.getName()))
						{
							newEntry.setMode(0b111_101_101);
						}

						copyEntry(archiveOutputStream, ais, tempEntry, newEntry);

						tempEntry = ais.getNextEntry();
					}
				}
			}

			// jdk check
			if(jdkArchivePath != null)
			{
				boolean mac = artifactName.contains("-mac-");
				buildBundledJRE(jdkArchivePath, factory, archiveOutType, archiveOutputStream, mac);
			}

			archiveOutputStream.finish();
		}
	}

	protected void buildBundledJRE(FilePath jdkArchivePath, ArchiveStreamFactory factory, String archiveOutType, ArchiveOutputStream archiveOutputStream, boolean mac) throws Exception
	{
		BundledJRE bundledJRE = new ArchivedBundledJRE(myBuildNumber, factory, jdkArchivePath, mac, archiveOutType, archiveOutputStream);

		bundledJRE.build();
	}

	protected static int extractMode(ArchiveEntry entry)
	{
		if(entry instanceof TarArchiveEntry)
		{
			return ((TarArchiveEntry) entry).getMode();
		}
		return entry.isDirectory() ? TarArchiveEntry.DEFAULT_DIR_MODE : TarArchiveEntry.DEFAULT_FILE_MODE;
	}

	protected static void copyEntry(ArchiveOutputStream archiveOutputStream, ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper<? extends ArchiveEntry> newEntry) throws IOException
	{
		byte[] data = null;
		if(!tempEntry.isDirectory())
		{
			try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream())
			{
				IOUtils.copy(ais, byteStream);
				data = byteStream.toByteArray();
			}

			// change line breaks
			try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
			{
				if(LineEnds.convert(new ByteArrayInputStream(data), stream, LineEnds.STYLE_UNIX))
				{
					data = stream.toByteArray();
				}
			}
			catch(BinaryDataException ignored)
			{
				// ignore binary data
			}
		}

		if(data != null)
		{
			newEntry.setSize(data.length);
		}

		archiveOutputStream.putArchiveEntry(newEntry.getItem());

		if(data != null)
		{
			IOUtils.copy(new ByteArrayInputStream(data), archiveOutputStream);
		}

		archiveOutputStream.closeArchiveEntry();
	}

	protected ArchiveEntryWrapper<? extends ArchiveEntry> createEntry(String type, String name, ArchiveEntry tempEntry)
	{
		name = replaceBuildDirectory(name);

		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new ArchiveEntryWrapper.Tar(name, tempEntry);
		}
		return new ArchiveEntryWrapper.Zip(name);
	}

	protected String replaceBuildDirectory(String entryName)
	{
		if(entryName.contains(ourBuildSNAPSHOT))
		{
			return entryName.replace(ourBuildSNAPSHOT, "build" + myBuildNumber);
		}
		return entryName;
	}

	protected OutputStream createOutputStream(String type, String prefix) throws Exception
	{
		final String fileName;
		if(type.equals(ArchiveStreamFactory.ZIP))
		{
			fileName = prefix + ".zip";
		}
		else if(type.equals(ArchiveStreamFactory.TAR))
		{
			fileName = prefix + ".tar.gz";
		}
		else
		{
			throw new IllegalArgumentException(type);
		}

		FilePath child = myTargetDir.child(fileName);

		final OutputStream outputStream = child.write();
		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new GzipCompressorOutputStream(outputStream);
		}
		return outputStream;
	}
}
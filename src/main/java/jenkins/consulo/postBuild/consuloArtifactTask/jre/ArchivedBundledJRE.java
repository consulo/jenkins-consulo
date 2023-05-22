/*
 * Copyright 2013-2023 must-be.org
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

package jenkins.consulo.postBuild.consuloArtifactTask.jre;

import com.Ostermiller.util.BinaryDataException;
import com.Ostermiller.util.LineEnds;
import hudson.FilePath;
import jakarta.annotation.Nonnull;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author VISTALL
 * @since 22/05/2023
 */
public class ArchivedBundledJRE extends BundledJRE<ArchiveEntry>
{
	private final String myArchiveOutType;
	private final ArchiveOutputStream myArchiveOutputStream;

	public ArchivedBundledJRE(int buildNumber, ArchiveStreamFactory factory, FilePath jdkFilePath, boolean isMac, String archiveOutType, ArchiveOutputStream archiveOutputStream)
	{
		super(buildNumber, factory, jdkFilePath, isMac);
		myArchiveOutType = archiveOutType;
		myArchiveOutputStream = archiveOutputStream;
	}

	@Nonnull
	protected ArchiveEntryWrapper<? extends ArchiveEntry> createEntry(String name, ArchiveEntry tempEntry)
	{
		name = replaceBuildDirectory(name);

		if(myArchiveOutType.equals(ArchiveStreamFactory.TAR))
		{
			return new ArchiveEntryWrapper.Tar(name, tempEntry);
		}
		return new ArchiveEntryWrapper.Zip(name);
	}

	@Override
	protected  void copyEntry(ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper<? extends ArchiveEntry> newEntry) throws IOException
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

		myArchiveOutputStream.putArchiveEntry(newEntry.getItem());

		if(data != null)
		{
			IOUtils.copy(new ByteArrayInputStream(data), myArchiveOutputStream);
		}

		myArchiveOutputStream.closeArchiveEntry();
	}
}

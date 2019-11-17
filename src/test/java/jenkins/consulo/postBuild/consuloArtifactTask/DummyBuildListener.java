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

import hudson.console.ConsoleNote;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author VISTALL
 * @since 2019-11-17
 */
public class DummyBuildListener implements BuildListener
{
	@Override
	public void started(List<Cause> list)
	{

	}

	@Override
	public void finished(Result result)
	{

	}

	@Override
	public PrintStream getLogger()
	{
		return System.out;
	}

	@Override
	public void annotate(ConsoleNote consoleNote) throws IOException
	{

	}

	@Override
	public void hyperlink(String s, String s1) throws IOException
	{

	}

	@Override
	public PrintWriter error(String s)
	{
		return null;
	}

	@Override
	public PrintWriter error(String s, Object... objects)
	{
		return null;
	}

	@Override
	public PrintWriter fatalError(String s)
	{
		return null;
	}

	@Override
	public PrintWriter fatalError(String s, Object... objects)
	{
		return null;
	}
}

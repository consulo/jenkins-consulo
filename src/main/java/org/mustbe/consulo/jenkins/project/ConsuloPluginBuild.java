package org.mustbe.consulo.jenkins.project;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import hudson.model.Build;

/**
 * @author VISTALL
 * @since 17.06.2015
 */
public class ConsuloPluginBuild extends Build<ConsuloPluginProject, ConsuloPluginBuild>
{
	public ConsuloPluginBuild(ConsuloPluginProject project) throws IOException
	{
		super(project);
	}

	public ConsuloPluginBuild(ConsuloPluginProject job, Calendar timestamp)
	{
		super(job, timestamp);
	}

	public ConsuloPluginBuild(ConsuloPluginProject project, File buildDir) throws IOException
	{
		super(project, buildDir);
	}
}

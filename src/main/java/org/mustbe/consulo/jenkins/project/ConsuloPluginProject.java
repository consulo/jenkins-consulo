package org.mustbe.consulo.jenkins.project;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.mustbe.consulo.jenkins.build.ConsuloBuilder;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import jenkins.model.Jenkins;

/**
 * @author VISTALL
 * @since 17.06.2015
 */
public class ConsuloPluginProject extends Project<ConsuloPluginProject, ConsuloPluginBuild> implements TopLevelItem
{
	@Restricted(NoExternalUse.class)
	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends AbstractProjectDescriptor
	{
		@Override
		public String getDisplayName()
		{
			return "Consulo plugin project";
		}

		@Override
		public ConsuloPluginProject newInstance(ItemGroup parent, String name)
		{
			ConsuloPluginProject consuloPluginProject = new ConsuloPluginProject(parent, name);
			consuloPluginProject.getBuildersList().add(new ConsuloBuilder());
			return consuloPluginProject;
		}
	}

	public ConsuloPluginProject(ItemGroup parent, String name)
	{
		super(parent, name);
	}

	@Override
	protected Class<ConsuloPluginBuild> getBuildClass()
	{
		return ConsuloPluginBuild.class;
	}

	@Override
	public TopLevelItemDescriptor getDescriptor()
	{
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
	}
}

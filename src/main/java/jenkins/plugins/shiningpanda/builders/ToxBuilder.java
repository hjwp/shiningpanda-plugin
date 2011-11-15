/*
 * ShiningPanda plug-in for Jenkins
 * Copyright (C) 2011 ShiningPanda S.A.S.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package jenkins.plugins.shiningpanda.builders;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import jenkins.plugins.shiningpanda.Messages;
import jenkins.plugins.shiningpanda.interpreters.Python;
import jenkins.plugins.shiningpanda.interpreters.Virtualenv;
import jenkins.plugins.shiningpanda.tools.PythonInstallation;
import jenkins.plugins.shiningpanda.util.BuilderUtil;
import jenkins.plugins.shiningpanda.workspace.Workspace;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ToxBuilder extends Builder implements Serializable
{

    /**
     * Path to the tox.ini file
     */
    public final String toxIni;

    /**
     * Force recreation of virtual environments
     */
    public final boolean recreate;

    @DataBoundConstructor
    public ToxBuilder(String toxIni, boolean recreate)
    {
        // Call super
        super();
        // Store the path to the tox.ini file
        this.toxIni = Util.fixEmptyAndTrim(toxIni);
        // Store the recreation flag
        this.recreate = recreate;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException,
            IOException
    {
        // Get the workspace
        Workspace workspace = Workspace.fromHome(build.getWorkspace());
        // Get the environment variables for this build
        EnvVars environment = BuilderUtil.getEnvironment(build, listener);

        Virtualenv virtualenv = workspace.getVirtualenv();

        if (virtualenv.isOutdated(BuilderUtil.lastConfigure(build)))
        {
            Python interpreter = PythonInstallation.getInterpreter(launcher.getChannel(), listener, environment);
            if (interpreter == null)
                return false;

            virtualenv.create(launcher, listener, workspace, interpreter, true, true);
        }

        virtualenv.pipInstall(launcher, listener, workspace, "tox");

        List<Python> interpreters = PythonInstallation.getInterpreters(launcher.getChannel(), listener, environment);
        Collections.reverse(interpreters);
        for (Python interpreter : interpreters)
        {
            environment.overrideAll(interpreter.getEnvironment());
        }
        return virtualenv.tox(launcher, listener, workspace, toxIni, recreate);
    }

    private static final long serialVersionUID = 1L;

    /**
     * Descriptor for this builder
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
    {
        /*
         * (non-Javadoc)
         * 
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.ToxBuilder_DisplayName();
        }

        /*
         * (non-Javadoc)
         * 
         * @see hudson.model.Descriptor#getHelpFile()
         */
        @Override
        public String getHelpFile()
        {
            return "/plugin/shiningpanda/help/ToxBuilder/help.html";
        }

        /*
         * (non-Javadoc)
         * 
         * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
         */
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
        {
            // Only available in matrix projects if some installations exist
            return !PythonInstallation.isEmpty() && BuilderUtil.isMatrix(jobType);
        }

        /**
         * Checks if the TOX configuration file is specified
         * 
         * @param project
         *            The linked project, to check permissions
         * @param value
         *            The value to check
         * @return The validation result
         */
        public FormValidation doCheckToxIni(@SuppressWarnings("rawtypes") @AncestorInPath AbstractProject project,
                @QueryParameter File value)
        {
            // Check that path is specified
            if (Util.fixEmptyAndTrim(value.getPath()) == null)
                return FormValidation.error(Messages.ToxBuilder_ToxIniRequired());
            // Do not need to check more as files are located on slaves
            return FormValidation.ok();
        }
    }
}
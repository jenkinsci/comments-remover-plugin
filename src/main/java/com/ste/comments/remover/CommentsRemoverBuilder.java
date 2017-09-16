package com.ste.comments.remover;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.zeroturnaround.zip.ZipUtil;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Symbol("commentsremover")
public class CommentsRemoverBuilder extends Builder implements SimpleBuildStep {

    private final static String COMMENTS_REMOVER_ARCHIVE_NAME = "comments_remover.zip";
    private final static String COMMENTS_REMOVER_ENTRY_FILE = "comments_remover.py";

    private final String filename;
    private final String language;
    private final String outputDir;

    @DataBoundConstructor
    public CommentsRemoverBuilder(String filename, String language, String outputDir) {
        this.filename = filename;
        this.language = language;
        this.outputDir = outputDir;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Invoking Comments Remover for filename: " + filename + " and language: " + language);
        File outputDirPath = setupOutputDir(workspace, outputDir);
        runCommentsRemoverProcess(workspace, launcher, listener, getDescriptor().getCommentsRemoverDir(), outputDirPath);
        listener.getLogger().println("Comments Remover finished processing. Output saved to directory: " + outputDirPath.getAbsoluteFile());
    }

    private File setupOutputDir(FilePath workspace, String outputDir) throws IOException {
        File outputDirPath = new File(workspace.getRemote() + File.separator + outputDir);
        if (outputDirPath.exists()) {
            FileUtils.deleteDirectory(outputDirPath);
        }
        outputDirPath.mkdir();
        return outputDirPath;
    }

    private void runCommentsRemoverProcess(FilePath workspace, Launcher launcher, TaskListener listener, File
            removerScriptDir, File outputDirPath) throws IOException, InterruptedException {

        String pythonPath = getDescriptor().getPythonPath();
        String pipPath = getDescriptor().getPipPath();

        String pipExecutable = StringUtils.isEmpty(pythonPath) ? "pip" : pipPath;
        String[] pipCommand = new String[]{pipExecutable, "install", "-r", removerScriptDir + File.separator + "requirements.txt", "-q"};
        listener.getLogger().println("Installing pip requirements [" + StringUtils.join(pipCommand, " ") + "]...");
        runProcess(launcher.launch().cmds(pipCommand).readStdout().start(), listener);

        String pythonExecutable = StringUtils.isEmpty(pythonPath) ? "python" : pythonPath;
        String[] commentsRemoverCommand = new String[]{pythonExecutable, removerScriptDir + File.separator + COMMENTS_REMOVER_ENTRY_FILE,
                workspace.getRemote() + File.separator + filename, language, outputDirPath.getAbsolutePath()};
        listener.getLogger().println("Executing script [" + StringUtils.join(commentsRemoverCommand, " ") + "]...");
        runProcess(launcher.launch().cmds(commentsRemoverCommand).readStdout().start(), listener);
    }

    private void runProcess(Proc process, TaskListener listener) throws IOException, InterruptedException {
        if (getDescriptor().getVerboseMode()) {
            /* Read the process's output */
            String inputLine;
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getStdout()));
            while ((inputLine = in.readLine()) != null ) {
                listener.getLogger().println(inputLine);
            }
        }
        process.joinWithTimeout(60, TimeUnit.SECONDS, listener);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String pythonPath;
        private String pipPath;
        private boolean verboseMode = true;
        private File commentsRemoverDir;

        public DescriptorImpl() throws IOException {
            load();
            unzipCommentsRemover();
        }

        private void unzipCommentsRemover() throws IOException {
            File jenkinsRootDir = Jenkins.getActiveInstance().getRootDir();
            File removerTargetDir = new File(jenkinsRootDir.getAbsolutePath() + File.separator + "comments_remover");
            if (removerTargetDir.exists()) {
                System.out.println("Removing old Comments Remover from: " + removerTargetDir);
                removerTargetDir.delete();
            }

            URL removerZipUrl = CommentsRemoverBuilder.class.getClassLoader().getResource(COMMENTS_REMOVER_ARCHIVE_NAME);
            if (removerZipUrl == null) {
                throw new RuntimeException("Failed to find Comments Remover archive in classpath (searched for name: " +
                        COMMENTS_REMOVER_ARCHIVE_NAME + "), failed to initialize Comments Remover plugin");
            }
            File removerZip = copyZipFromResources(removerZipUrl, jenkinsRootDir);
            System.out.println("Unpacking Comments Remover from: " + removerZipUrl.getFile() + " to " + removerTargetDir);
            ZipUtil.unpack(removerZip, removerTargetDir);
            removerZip.delete();
            commentsRemoverDir = removerTargetDir;
        }

        private File copyZipFromResources(URL removerUrl, File targetDir) throws IOException {
            InputStream in = new BufferedInputStream(removerUrl.openStream());
            File zip = File.createTempFile("remover", ".zip", targetDir);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(zip));
            copyInputStream(in, out);
            out.close();
            return zip;
        }

        private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            while (len >= 0) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
            in.close();
            out.close();
        }

        public FormValidation doCheckFilename(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a filename");
            return FormValidation.ok();
        }

        public FormValidation doCheckLanguage(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a language");
            return FormValidation.ok();
        }

        public FormValidation doCheckOutputDir(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set output directory");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Invoke Comments Remover";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            pythonPath = formData.getString("pythonPath");
            pipPath = formData.getString("pipPath");
            verboseMode = formData.getBoolean("verboseMode");
            save();
            return super.configure(req, formData);
        }

        public String getPythonPath() {
            return pythonPath;
        }

        public String getPipPath() {
            return pipPath;
        }

        public boolean getVerboseMode() {
            return verboseMode;
        }

        public File getCommentsRemoverDir() {
            return commentsRemoverDir;
        }
    }

    public String getFilename() {
        return filename;
    }

    public String getLanguage() {
        return language;
    }

    public String getOutputDir() {
        return outputDir;
    }
}


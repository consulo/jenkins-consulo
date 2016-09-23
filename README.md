# Instruction how setup project for building Consulo and/or plugins in Jenkins

## Setup Jenkins
 * required 'Git' plugin
 * required 'Consulo Integration Plugin' (this repo)

## Creating plugin project
 * Free-style project with any name
   ![](http://klikr.org/9f4704e53b7044bcf4e2a00f02fa.png)
 * Source management
   ![](http://klikr.org/6985522929d57e25465434a16241.png)
 * If plugin have dependencies, need add build trigger and/or check build on GH change
   ![](http://klikr.org/1e6a3a11830b3145f935af30ee64.png)
 * Build tasks (invoke cold only)
   ![](http://klikr.org/af2d16197f8442d52491f8531bb8.png)
 * Post build tasks
    * create plugin artifacts (Consulo) - need create zip file from plugin directory
    * trait this artifacts as Jenkins artifact
    * deploy plugin to plugin manager
    * set build status to commit at github

   ![](http://klikr.org/a4d5437d448d7c21a35d0e0faf26.png)

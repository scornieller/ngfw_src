# Sebastien Delafond <seb@untangle.com>
# Dirk Morris <dmorris@untangle.com>


ENV["JAVA_HOME"] = "/usr/lib/jvm/java-6-sun"

POTENTIAL_SRC_HOMES = [ ENV['SRC_HOME'], '../../work/src', '../../src' ]
POTENTIAL_SRC_HOMES << '.' unless `pwd` =~ /hades/
SRC_HOME = POTENTIAL_SRC_HOMES.compact.find do |d|
  File.exist?(d)
end
puts "SRC_HOME = #{SRC_HOME}"

$DevelBuild = ARGV.grep(/install/).empty?
puts "DevelBuild = #{$DevelBuild}"

## This is how you define where the stamp file will go
module Rake
  SF = "./taskstamps.txt"
  
  if $DevelBuild and ENV["USER"] != "buildbot" then
    StampFile = "#{SRC_HOME}/#{SF}"
  else
    StampFile = SF
  end
end

require "./buildtools/stamp-task.rb"
require "./buildtools/rake-util.rb"
require "./buildtools/target.rb"
require "./buildtools/jars.rb"
require "./buildtools/c-compiler.rb"
require "./buildtools/node.rb"



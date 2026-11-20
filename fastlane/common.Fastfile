# install fastlane via bundle
# cd fastlane
# bundle config set path fastlane
# bundle install 
# bundle exec fastlane add_plugin huawei_appgallery_connect

# run fastlane
# bundle exec fastlane 

# update fastlane
# bundle update fastlane

# update plugins
# bundle exec fastlane update_plugins

require "json"
require "net/http"
require "open3"
require "tmpdir"

BUILD_NUMBER_RC_START = 51
BUILD_NUMBER_FINAL_START = 90

MAJOR_MULTIPLIER = 10000000
MINOR_MULTIPLIER = 10000
PATCH_MULTIPLIER = 100

FDROID_ABI_VERSION_CODES = { "armeabi-v7a" => 1, "arm64-v8a" => 2, "x86" => 3, "x86_64" => 4 }.freeze
FDROID_ABI_MULTIPLIER = 10

TYPE_ALPHA = "alpha"
TYPE_RC = "rc"
TYPE_FINAL = "final"
# increment types to the next hotfix, minor or major version: version part to increment and resulting build number
NEXT_VERSION_TYPES = {
    "hotfix-alpha" => ["patch", 0],
    "hotfix-rc" => ["patch", BUILD_NUMBER_RC_START],
    "hotfix-final" => ["patch", BUILD_NUMBER_FINAL_START],
    "minor-alpha" => ["minor", 0],
    "minor-rc" => ["minor", BUILD_NUMBER_RC_START],
    "minor-final" => ["minor", BUILD_NUMBER_FINAL_START],
    "major-alpha" => ["major", 0],
}.freeze
# former names of increment types
INCREMENT_TYPE_ALIASES = { "patch" => "hotfix-alpha", "minor" => "minor-alpha", "major" => "major-alpha" }.freeze
INCREMENT_TYPES_USAGE = "type:(rc|final|#{NEXT_VERSION_TYPES.keys.join("|")})"

GRADLE_BUNDLE_PATH = "app/build/outputs/bundle/gplayRelease/app-gplay-release.aab" # moved to bundlePath after the build
BUILT_COMMIT_PATH = "app/build/outputs/releaseCommit.txt"
CHANGELOG_DIR = "metadata/android/en-US/changelogs"
RELEASE_NOTES_GUIDE = ".claude/skills/suggest-release-notes/SKILL.md"
CHANGELOG_MAX_LENGTH = 500 # Google Play limit for release notes

default_platform(:android)

ENV['SUPPLY_UPLOAD_MAX_RETRIES']='10'

# Secrets are not read from the environment but asked for when a release lane starts.
# Filled by askForSigningPasswords, merged into the app's *_SIGNING_PROPERTIES at build time.
SIGNING_STORE_PASSWORD_KEY = "android.injected.signing.store.password"
SIGNING_KEY_PASSWORD_KEY = "android.injected.signing.key.password"
SIGNING_PASSWORDS = {}
# signed builds neither store the signing configuration in the configuration cache nor keep it in a daemon.
# The Kotlin compiler runs inside the build, as a Kotlin daemon started by it would keep the passwords in its
# environment after the build.
SIGNED_BUILD_GRADLE_FLAGS = ["--no-configuration-cache", "--no-daemon", "-Pkotlin.compiler.execution.strategy=in-process"]

platform :android do
	# Usage: fastlane incrementVersion [type:(rc|final|hotfix-alpha|hotfix-rc|hotfix-final|minor-alpha|minor-rc|minor-final|major-alpha)]
    # For rc, final: will set build number to first rc/first final or increment it by 1
    # For hotfix-*, minor-*, major-*: will increment that version number by 1, set the smaller ones to 0
    #   and set the build number to the first alpha, rc or final
    # Without type, the resulting versions of all types are shown to choose from.
    # rc or final after a final release asks for the next hotfix or minor version, as a released version
    # cannot get another rc or final.
    desc "Preparation phase 1: Increment version code and version name"
    lane :incrementVersion do |options|
        versionInfo = getVersionInfo()
        versionComponents = parseVersionCode(versionInfo)
        type = options[:type] || chooseIncrementType(versionInfo: versionInfo, versionComponents: versionComponents)
        if type == TYPE_RC && versionComponents["type"] == TYPE_FINAL
            type = chooseIncrementType(versionInfo: versionInfo, versionComponents: versionComponents,
                types: ["hotfix-rc", "minor-rc"])
        elsif type == TYPE_FINAL && versionComponents["type"] == TYPE_FINAL
            type = chooseIncrementType(versionInfo: versionInfo, versionComponents: versionComponents,
                types: ["hotfix-final", "minor-final"])
        end
        newVersionComponents = incrementVersionComponents(versionComponents: versionComponents, type: type)
        versionNameGenerated = generateVersionName(newVersionComponents)
        versionCodeGenerated = generateVersionCode(newVersionComponents)

        promptYesNo(
            title: "Check the new version",
            text: "Version name: #{versionInfo["versionName"]} -> #{versionNameGenerated}\n" +
                "Version code: #{versionInfo["versionCode"]} -> #{versionCodeGenerated}",
            question: "Change the version in app/build.gradle.kts to #{versionNameGenerated}?"
        )
        writeVersions(versionCode: versionCodeGenerated, versionName: versionNameGenerated)
        summary = ["Version: #{versionInfo["versionName"]} (#{versionInfo["versionCode"]}) -> " +
            "#{versionNameGenerated} (#{versionCodeGenerated}), changed in app/build.gradle.kts"]
        if newVersionComponents["type"] == TYPE_ALPHA
            summary << "Next: commit app/build.gradle.kts"
        else
            summary += createChangelogFile(versionCode: versionCodeGenerated)
        end
        printAtExit(title: "Version changed", lines: summary)
    end

    desc "Preparation phase 2: Commit version change and changelog, releasePhase2 pushes them"
    lane :commitVersion do
        versionInfo = getVersionInfo()
        checkReleasable(parseVersionCode(versionInfo))
        checkChangelogFile(versionCode: versionInfo["versionCode"])

        gradleFile = "../app/build.gradle.kts"
        changelogFile = changelogPath(versionCode: versionInfo["versionCode"])
        message = "bump version to #{versionInfo["versionName"]}"
        changelog = File.read(changelogFile, encoding: "UTF-8")
        promptYesNo(
            title: "Check the commit and the changelog",
            text: "Commit message: #{message}\n" +
                "Files: app/build.gradle.kts, fastlane/#{changelogFile}\n" +
                "Changelog (#{changelog.strip.length} of #{CHANGELOG_MAX_LENGTH} characters), " +
                "used for Google Play and the GitHub release:\n\n" +
                changelog,
            question: "Commit the version change and this changelog?"
        )
        sh("git", "add", "--", gradleFile, changelogFile)
        sh("git", "commit", "--signoff", "-m", message, "--", gradleFile, changelogFile)

        printAtExit(title: "Version committed", lines: [
            "Committed: #{last_git_commit[:abbreviated_commit_hash]} #{message}",
            "Not pushed: releasePhase2 pushes the branch after releasePhase1 has tested and built it",
            "Next: run releasePhase1",
        ])
    end

    desc "Release phase 1: Run tests and build app"
    lane :releasePhase1 do
		warnAboutUnusedSecrets()
		ensureCleanWorkingTree()
		askForSigningPasswords()
		builtCommit = last_git_commit[:commit_hash]
		# may hold signing passwords of releases before signed builds were run without configuration cache
		sh("rm -rf ../.gradle/configuration-cache")
    	sh("rm -rf ../release/ && mkdir -p ../release")
		gradle(task: 'clean')

        versionInfo = getVersionInfo()
        test() # needs to be overwritten in apps

        apks = buildRelease(versionCode: versionInfo["versionCode"])
		File.write("../#{BUILT_COMMIT_PATH}", builtCommit)

		printAtExit(title: "Release phase 1 finished", lines: [
			"Version #{versionInfo["versionName"]} (#{versionInfo["versionCode"]}), built from commit #{builtCommit}",
			"App bundle for Google Play: #{File.expand_path("../#{bundlePath(versionCode: versionInfo["versionCode"])}")}",
			*apks.map { |apk| "APK: #{File.expand_path(apk[:path])} (signed by #{apk[:signer]})" },
			"Next: test the APKs, then run releasePhase2DryRun and releasePhase2",
			"If you find a bug: fix it, commit, and run releasePhase1 again",
		])
    end

    desc "Release phase 2: checks, tag, create GH release, upload to appstores"
	lane :releasePhase2 do
		releasePhase2Steps(dryRun: false)
	end

	desc "Release phase 2 dry run: run all checks, show what would be published"
	lane :releasePhase2DryRun do
		releasePhase2Steps(dryRun: true)
	end

	private_lane :releasePhase2Steps do |options|
		dryRun = options[:dryRun]
		warnAboutUnusedSecrets()
		ensureCleanWorkingTree()
		push = checkBranchPushable(dryRun: dryRun)
		checkArtifactsCommit()
		versionInfo = getVersionInfo()
		versionComponents = parseVersionCode(versionInfo)
		checkReleasable(versionComponents)
		checkChangelogFile(versionCode: versionInfo["versionCode"])
		githubToken = askForGithubToken()
		storeTrack = getPlayStoreTrack(versionComponents)
		isPrerelease = isPrerelease(versionComponents)
		huaweiCredentials = uploadsToHuawei(isPrerelease: isPrerelease) ? askForHuaweiCredentials() : nil
		tagName = getTagName(versionComponents)
		preReleaseChecks(versionInfo: versionInfo, tagName: tagName, type: versionComponents["type"], track: storeTrack, isPrerelease: isPrerelease, push: push, dryRun: dryRun)
		checkArtifactsExist(versionCode: versionInfo["versionCode"])
		repoSpecificChecks(isPrerelease: isPrerelease)  # needs to be overwritten in apps
		summary = [
			pushBranch(push: push, dryRun: dryRun),
			tag(name: tagName, dryRun: dryRun),
			createGithubRelease(token: githubToken, name: versionInfo["versionName"], tag: tagName, versionCode: versionInfo["versionCode"], isPrerelease: isPrerelease, dryRun: dryRun),
			uploadToPlayStore(track: storeTrack, versionCode: versionInfo["versionCode"], dryRun: dryRun),
			uploadToHuawei(versionCode: versionInfo["versionCode"], credentials: huaweiCredentials, dryRun: dryRun),
		].compact
		if dryRun
			summary += push[:problems].map { |message| "Would fail in releasePhase2: #{message}" }
			summary << (push[:problems].empty? ? "Next: run releasePhase2" : "Next: fix the above, then run releasePhase2")
			printAtExit(title: "Dry run of #{versionInfo["versionName"]} finished, nothing was published", lines: summary)
		else
			summary << "Next: check the GitHub release and the release in the Google Play Console"
			printAtExit(title: "#{versionInfo["versionName"]} released", lines: summary)
		end
	end

    desc "Build ABI APKs for F-Droid in app/build/fdroid/"
    lane :buildFdroid do
        gradle(
            task: ":app:assembleGenericRelease",
            flags: "--init-script fastlane/fdroid.init.gradle",
            properties: { "fdroidAbiSplit" => true }
        )
    end

    desc "Print version info"
    lane :printVersionInfo do
        versionInfo = getVersionInfo()
        versionComponents = parseVersionCode(versionInfo)
        printAtExit(title: "Version info", lines: [
            "Version code: #{versionInfo["versionCode"]}",
            "Version name: #{versionInfo["versionName"]}",
            "Major: #{versionComponents["major"]}",
            "Minor: #{versionComponents["minor"]}",
            "Patch: #{versionComponents["patch"]}",
            "Build: #{versionComponents["build"]}",
        ] + fdroidAbiVersionCodes(versionCode: versionInfo["versionCode"]).map { |abi, versionCode|
            "F-Droid #{abi} version code: #{versionCode}"
        })
    end

    desc "Upload Alpha version to play store"
	lane :uploadAlphaToPlayStore do |options|
		upload_to_play_store(
			skip_upload_images: true,
			skip_upload_aab: true,
			skip_upload_changelogs: true,
			skip_upload_metadata: true,
			skip_upload_screenshots: true,
			track: 'alpha',
			apk: "/home/androiddaily/apks-#{APP}/android-#{APP}-" + options[:version] + ".apk",
		)
	end


    desc "Parse major, minor, patch and build from versionCode"
    private_lane :parseVersionCode do |versionInfo|
        versionCode = versionInfo["versionCode"]
        build = versionCode % 100
        patch = (versionCode / PATCH_MULTIPLIER) % 100
        minor = (versionCode / MINOR_MULTIPLIER) % 100
        major = (versionCode / MAJOR_MULTIPLIER) % 100

        type = getVersionType(build: build)


        { "major" => major, "minor" => minor, "patch" => patch, "build" => build, "type" => type }
    end

    desc "Let the user choose the version increment type, showing the resulting version of each"
    private_lane :chooseIncrementType do |options|
        types = options[:types] || (options[:versionComponents]["type"] == TYPE_FINAL ?
            NEXT_VERSION_TYPES.keys :
            [TYPE_RC, TYPE_FINAL, *NEXT_VERSION_TYPES.keys.grep(/-alpha$/)])
        choices = types.map do |type|
            components = incrementVersionComponents(versionComponents: options[:versionComponents].dup, type: type)
            [generateVersionCode(components), type, generateVersionName(components)]
        end.sort.to_h { |code, type, name| ["#{type.ljust(12)}  #{name} (#{code})", type] }
        UI.message("Current version: #{options[:versionInfo]["versionName"]} (#{options[:versionInfo]["versionCode"]})")
        choices[UI.select("Increment to:", choices.keys)]
    end

    desc "Get version type from build number"
    private_lane :getVersionType do |options|
        build = options[:build]
        if build < BUILD_NUMBER_RC_START
            type = TYPE_ALPHA
        elsif build < BUILD_NUMBER_FINAL_START
            type = TYPE_RC
        else
            type = TYPE_FINAL
        end
        type
    end

    desc "Generate versionCode from version components"
    private_lane :generateVersionCode do |versionComponents|
        major = versionComponents["major"]
        minor = versionComponents["minor"]
        patch = versionComponents["patch"]
        build = versionComponents["build"]
        test = major * MAJOR_MULTIPLIER + minor * MINOR_MULTIPLIER + patch * PATCH_MULTIPLIER + build
        test
    end

    desc "Generate F-Droid versionCode per ABI, must match fdroid.init.gradle"
    private_lane :fdroidAbiVersionCodes do |options|
        versionCode = options[:versionCode]
        base = (versionCode / MAJOR_MULTIPLIER) * MAJOR_MULTIPLIER + (versionCode % MAJOR_MULTIPLIER) * FDROID_ABI_MULTIPLIER
        FDROID_ABI_VERSION_CODES.transform_values { |abiCode| base + abiCode }
    end

    desc "Compute version name from version code"
    private_lane :generateVersionName do |versionComponents|
        suffix = ""
        buildNumber = versionComponents["build"]
        case
          when versionComponents["type"] == TYPE_RC
            rcNumber = (buildNumber - BUILD_NUMBER_RC_START) + 1
            suffix = " RC#{rcNumber}"
          when versionComponents["type"] == TYPE_ALPHA
            suffix = " Alpha#{buildNumber + 1}"
        end
        "#{versionComponents["major"]}.#{versionComponents["minor"]}.#{versionComponents["patch"]}#{suffix}"
    end

    desc "Read versions from gradle file"
    private_lane :getVersionInfo do
        File.open("../app/build.gradle.kts","r") do |file|
            text = file.read
            versionName = text.match(/versionName = "(.*)"$/)[1]
            versionCode = text.match(/versionCode = ([0-9]*)$/)[1].to_i

            { "versionCode" => versionCode, "versionName" => versionName }
        end
    end

    desc "Get the path of the release bundle for a version code, relative to the repository root"
    private_lane :bundlePath do |options|
        "release/nextcloud-#{APP}-gplay-#{options[:versionCode]}.aab"
    end

    desc "Get the changelog file path for a version code, relative to the fastlane folder"
    private_lane :changelogPath do |options|
        "#{CHANGELOG_DIR}/#{options[:versionCode]}.txt"
    end

    desc "Create empty changelog file for the version code if it does not exist yet, returns what was done and the next steps"
    private_lane :createChangelogFile do |options|
        path = changelogPath(versionCode: options[:versionCode])
        if File.exist?(path)
            summary = ["Changelog: already exists, fastlane/#{path}", "Next: check the changelog"]
        else
            FileUtils.mkdir_p(CHANGELOG_DIR)
            File.write(path, "")
            summary = ["Changelog: created empty file fastlane/#{path}"]
            if File.exist?("../#{RELEASE_NOTES_GUIDE}")
                summary << "Next: fill the changelog, see #{RELEASE_NOTES_GUIDE}, or run /suggest-release-notes in Claude Code"
            else
                summary << "Next: fill the changelog"
            end
        end
        summary << "Next: run commitVersion"
    end

    desc "Print the lines when fastlane exits, after its summary, so they are the last output"
    private_lane :printAtExit do |options|
        at_exit do
            puts("")
            UI.header(options[:title])
            options[:lines].each { |line| UI.important(line) }
        end
    end

    desc "Check that the changelog file for the version code exists, is not empty and fits into Google Play"
    private_lane :checkChangelogFile do |options|
        path = changelogPath(versionCode: options[:versionCode])
        if !File.exist?(path)
            UI.user_error!("Changelog not found: fastlane/#{path}")
        end
        # independent of the locale, Google Play counts characters of UTF-8 text
        text = File.read(path, encoding: "UTF-8")
        if !text.valid_encoding?
            UI.user_error!("Changelog is not UTF-8 encoded: fastlane/#{path}")
        end
        length = text.strip.length
        if length == 0
            UI.user_error!("Changelog is empty: fastlane/#{path}")
        end
        if length > CHANGELOG_MAX_LENGTH
            UI.user_error!("Changelog has #{length} characters, maximum is #{CHANGELOG_MAX_LENGTH}: fastlane/#{path}")
        end
    end

    desc "Write versions to gradle file"
    private_lane :writeVersions do |options|
        File.open("../app/build.gradle.kts","r+") do |file|
            text = file.read
            text.sub!(/versionName = "(.*)"$/, "versionName = \"#{options[:versionName]}\"")
            text.sub!(/versionCode = ([0-9]*)$/, "versionCode = #{options[:versionCode]}")
            file.rewind
            file.write(text)
            file.truncate(file.pos)
        end
    end

    private_lane :incrementVersionComponents do |options|
        versionComponents = options[:versionComponents]
        type = INCREMENT_TYPE_ALIASES.fetch(options[:type], options[:type])
        if NEXT_VERSION_TYPES.key?(type)
            part, build = NEXT_VERSION_TYPES[type]
            case part
            when "major"
                versionComponents["major"] = versionComponents["major"] + 1
                versionComponents["minor"] = 0
                versionComponents["patch"] = 0
            when "minor"
                versionComponents["minor"] = versionComponents["minor"] + 1
                versionComponents["patch"] = 0
            when "patch"
                versionComponents["patch"] = versionComponents["patch"] + 1
            end
            versionComponents["build"] = build
        elsif type == TYPE_RC
            if versionComponents["type"] == TYPE_FINAL
                UI.user_error!("There is no rc after a final release, use hotfix-rc or minor-rc")
            elsif versionComponents["type"] != TYPE_RC
                versionComponents["build"] = BUILD_NUMBER_RC_START
            else
                versionComponents["build"] = versionComponents["build"] + 1
            end
        elsif type == TYPE_FINAL
            if versionComponents["type"] == TYPE_FINAL
                UI.user_error!("There is no further final of a released version, use hotfix-final or minor-final")
            end
            versionComponents["build"] = BUILD_NUMBER_FINAL_START
        else
            UI.user_error!("Unknown or missing version increment type #{options[:type]}. Usage: incrementVersion #{INCREMENT_TYPES_USAGE}")
        end
        versionComponents["type"] = getVersionType(build: versionComponents["build"])
        versionComponents
    end

    desc "Get tag name from version components"
    private_lane :getTagName do |versionComponents|
        if versionComponents["type"] == TYPE_FINAL
            tag = "#{versionComponents["major"]}.#{versionComponents["minor"]}.#{versionComponents["patch"]}"
        elsif versionComponents["type"] == TYPE_RC
            rcNumber = (versionComponents["build"] - BUILD_NUMBER_RC_START) + 1
            rcNumberPadded = "%02d" % rcNumber
            tag = "rc-#{versionComponents["major"]}.#{versionComponents["minor"]}.#{versionComponents["patch"]}-#{rcNumberPadded}"
        else
            UI.user_error!("Build number cannot be tagged: #{versionComponents["build"]}")
        end
    end

    desc "Check if version is releasable"
    private_lane :checkReleasable do |versionComponents|
        if versionComponents["type"] != TYPE_FINAL && versionComponents["type"] != TYPE_RC
            UI.user_error!("Version is not releasable: #{versionComponents["type"]}")
        end
    end

    desc "Get play store track from version type"
    private_lane :getPlayStoreTrack do |versionComponents|
        case versionComponents["type"]
        when TYPE_RC
            track = "beta"
        when TYPE_FINAL
            track = "production"
        else
            UI.user_error!("Version is not releasable: #{versionComponents["type"]}")
        end
    end

    desc "check if release is a prerelease"
	private_lane :isPrerelease do |versionComponents|
		case versionComponents["type"]
		when TYPE_RC
			isPrerelease = true
		when TYPE_FINAL
			isPrerelease = false
		else
			UI.user_error!("Version is not releasable: #{versionComponents["type"]}")
		end
	end

	desc "Run a signed Gradle build, the signing properties are only in the environment of the Gradle process"
	private_lane :signedGradleBuild do |options|
		env = options[:properties].to_h { |key, value| ["ORG_GRADLE_PROJECT_#{key}", value.to_s] }
		# bash instead of the /bin/sh of gradlew, as dash drops environment variables with dots in their names
		if !system(env, "bash", "./gradlew", *SIGNED_BUILD_GRADLE_FLAGS, *options[:tasks], chdir: "..")
			UI.user_error!("Gradle tasks failed: #{options[:tasks].join(" ")}")
		end
	end

	desc "Build signed app bundle and APKs, with one Gradle run per keystore"
	private_lane :buildRelease do |options|
		apkFlavors = APP == "files" ? ["generic", "gplay", "huawei"] : ["generic", "gplay"]
		bundleTasks = ["bundleGplayRelease"]
		apkTasks = apkFlavors.map { |flavor| "assemble#{flavor.capitalize}Release" }
		uploadProperties = GRADLE_SIGNING_PROPERTIES.merge(SIGNING_PASSWORDS.fetch(:gradle))
		if defined?(APK_SIGNING_PROPERTIES)
			signedGradleBuild(tasks: bundleTasks, properties: uploadProperties)
			signedGradleBuild(tasks: apkTasks, properties: APK_SIGNING_PROPERTIES.merge(SIGNING_PASSWORDS.fetch(:apk)))
		else
			signedGradleBuild(tasks: bundleTasks + apkTasks, properties: uploadProperties)
		end

		bundle = bundlePath(versionCode: options[:versionCode])
		# out of Gradle's output folder, which other builds overwrite
		sh("mv ../#{GRADLE_BUNDLE_PATH} ../#{bundle}")
		checkBundleSigned(path: bundle)
		apkFlavors.map do |flavor|
			apk = "../release/nextcloud-#{APP}-#{flavor}-#{options[:versionCode]}.apk"
			sh("mv ../app/build/outputs/apk/#{flavor}/release/*.apk #{apk}")
			{ path: apk, signer: checkApkSigned(path: apk) }
		end
	end

	# A build that does not get the signing properties produces unsigned artifacts without failing.
	desc "Check that the app bundle is signed"
	private_lane :checkBundleSigned do |options|
		jarsigner = ENV["JAVA_HOME"] ? File.join(ENV["JAVA_HOME"], "bin", "jarsigner") : "jarsigner"
		begin
			output, status = Open3.capture2e(jarsigner, "-J-Duser.language=en", "-verify", "-strict", "../#{options[:path]}")
		rescue Errno::ENOENT
			UI.user_error!("jarsigner not found, set JAVA_HOME or add jarsigner to PATH")
		end
		# exit code bits: 1 verification failed, 8 wrong key usage, 16 unsigned entries.
		# 4 (certificate chain not validated) is expected for self-signed upload keys.
		if status.exitstatus.nil? || (status.exitstatus & (1 | 8 | 16)) != 0
			UI.user_error!("App bundle is not signed correctly: #{options[:path]}\n#{output.strip}")
		end
	end

	desc "Check that an APK is signed, returns the certificate DN of the signer"
	private_lane :checkApkSigned do |options|
		sdk = ENV["ANDROID_HOME"] || ENV["ANDROID_SDK_ROOT"]
		if sdk.nil? && File.exist?("../local.properties")
			sdk = File.read("../local.properties")[/^sdk\.dir=(.*)$/, 1]
		end
		apksigner = sdk && Dir.glob(File.join(sdk, "build-tools", "*", "apksigner"))
			.max_by { |path| File.basename(File.dirname(path)).split(/[.-]/).map(&:to_i) }
		if apksigner.nil?
			UI.user_error!("apksigner not found, set ANDROID_HOME")
		end
		output, status = Open3.capture2e(apksigner, "verify", "--print-certs", options[:path])
		if !status.success?
			UI.user_error!("APK is not signed correctly: #{options[:path]}\n#{output.strip}")
		end
		signer = output[/certificate DN: (.*)$/, 1].to_s.strip
		UI.message("#{File.basename(options[:path])}: signed by #{signer}")
		signer
	end

	desc "Ask for keystore passwords (upload keystore, and APK keystore if APK_SIGNING_PROPERTIES is defined)"
	private_lane :askForSigningPasswords do
		# before any password is asked for, so a missing setting does not waste the typing
		checkSigningSettings(name: "Upload", constant: "GRADLE_SIGNING_PROPERTIES", properties: GRADLE_SIGNING_PROPERTIES)
		if defined?(APK_SIGNING_PROPERTIES)
			checkSigningSettings(name: "APK", constant: "APK_SIGNING_PROPERTIES", properties: APK_SIGNING_PROPERTIES)
		end
		SIGNING_PASSWORDS[:gradle] = {
			SIGNING_STORE_PASSWORD_KEY => promptSecret(text: "Enter Upload keystore password:"),
			SIGNING_KEY_PASSWORD_KEY => promptSecret(text: "Enter Upload key password:"),
		}
		checkSigningPasswords(name: "Upload", properties: GRADLE_SIGNING_PROPERTIES.merge(SIGNING_PASSWORDS[:gradle]))
		if defined?(APK_SIGNING_PROPERTIES)
			SIGNING_PASSWORDS[:apk] = {
				SIGNING_STORE_PASSWORD_KEY => promptSecret(text: "Enter APK keystore password:"),
				SIGNING_KEY_PASSWORD_KEY => promptSecret(text: "Enter APK key password:"),
			}
			checkSigningPasswords(name: "APK", properties: APK_SIGNING_PROPERTIES.merge(SIGNING_PASSWORDS[:apk]))
		end
	end

	desc "Check that keystore file and key alias are set and the keystore exists"
	private_lane :checkSigningSettings do |options|
		properties = options[:properties]
		# the app's Fastfile reads keystore file and key alias from environment variables, listed in its header comment
		hint = "Set the environment variable that #{options[:constant]} in fastlane/Fastfile reads for it, " +
			"e.g. in your shell configuration, see the comment at the top of fastlane/Fastfile."
		{ "android.injected.signing.store.file" => "file", "android.injected.signing.key.alias" => "key alias" }.each do |key, what|
			if properties[key].to_s.strip.empty?
				UI.user_error!("#{options[:name]} keystore #{what} is not set (#{key}). #{hint}")
			end
		end
		storeFile = File.expand_path(properties["android.injected.signing.store.file"], "..")
		if !File.file?(storeFile)
			UI.user_error!("#{options[:name]} keystore not found: #{storeFile}. Check the path. #{hint}")
		end
	end

	desc "Check keystore password, key alias and key password, so mistakes are found before the build"
	private_lane :checkSigningPasswords do |options|
		properties = options[:properties]
		storeFile = File.expand_path(properties["android.injected.signing.store.file"], "..")
		# Loads the key like the signing does. keytool cannot be used, it ignores the key password of PKCS12 keystores.
		source = <<~JAVA
			import java.io.File;
			import java.security.KeyStore;

			public class CheckKeystore {
			    public static void main(String[] args) {
			        try {
			            KeyStore keyStore = KeyStore.getInstance(new File(args[0]),
			                System.getenv("FASTLANE_CHECK_STORE_PASSWORD").toCharArray());
			            if (!keyStore.isKeyEntry(args[1])) {
			                System.err.println("Key alias not found: " + args[1]);
			                System.exit(1);
			            }
			            keyStore.getKey(args[1], System.getenv("FASTLANE_CHECK_KEY_PASSWORD").toCharArray());
			        } catch (Exception e) {
			            System.err.println(e);
			            System.exit(1);
			        }
			    }
			}
		JAVA
		java = ENV["JAVA_HOME"] ? File.join(ENV["JAVA_HOME"], "bin", "java") : "java"
		# passwords are handed over as environment of the java process only, so they do not show up in ps
		env = {
			"FASTLANE_CHECK_STORE_PASSWORD" => properties[SIGNING_STORE_PASSWORD_KEY],
			"FASTLANE_CHECK_KEY_PASSWORD" => properties[SIGNING_KEY_PASSWORD_KEY],
		}
		output = nil
		status = nil
		Dir.mktmpdir do |dir|
			sourceFile = File.join(dir, "CheckKeystore.java")
			File.write(sourceFile, source)
			begin
				output, status = Open3.capture2e(env, java, sourceFile, storeFile,
					properties["android.injected.signing.key.alias"].to_s)
			rescue Errno::ENOENT
				UI.user_error!("java not found, set JAVA_HOME or add java to PATH")
			end
		end
		if !status.success?
			UI.user_error!("#{options[:name]} keystore check failed: #{output.lines.last.to_s.strip}")
		end
	end

	desc "Warn about passwords and tokens set in the environment, which are asked for instead"
	private_lane :warnAboutUnusedSecrets do
		names = ENV.keys.grep(/^FASTLANE_.+_(PASSWORD|GITHUB_API_TOKEN|HUAWEI_CLIENT_ID|HUAWEI_CLIENT_SECRET|HUAWEI_APP_ID)$/).sort
		if !names.empty?
			UI.important("Not used, the values are asked for instead. Remove them from your shell configuration: #{names.join(", ")}")
		end
	end

	desc "Ask for GitHub API token and check that it can push to the repository"
	private_lane :askForGithubToken do
		token = promptSecret(text: "Enter GitHub API token:").strip
		result = githubGet(token: token, path: "/repos/#{REPO}")
		if result[:status] != 200
			UI.user_error!("GitHub API token was rejected (HTTP #{result[:status]})")
		end
		# permissions of the account, the token may have less
		if result[:json].dig("permissions", "push") != true
			UI.user_error!("Your GitHub account has no push permission for #{REPO}")
		end
		if result[:scopes].nil?
			# fine-grained tokens do not report their permissions
			UI.important("Permissions of fine-grained tokens cannot be checked. The token needs read and write access to Contents of #{REPO}.")
		else
			scopes = result[:scopes].split(",").map(&:strip)
			if (scopes & ["repo", "public_repo"]).empty?
				UI.user_error!("GitHub API token needs the scope repo or public_repo, it has: #{scopes.empty? ? "none" : scopes.join(", ")}")
			end
		end
		token
	end

	desc "Ask for the Huawei AppGallery Connect API client and check it before anything is published. " +
		"The app ID is taken from HUAWEI_APP_ID in the app's Fastfile, or asked for."
	private_lane :askForHuaweiCredentials do
		clientId = promptSecret(text: "Enter Huawei AppGallery Connect API client ID:").strip
		clientSecret = promptSecret(text: "Enter Huawei AppGallery Connect API client secret:").strip
		appId = defined?(HUAWEI_APP_ID) ? HUAWEI_APP_ID : prompt(text: "Enter Huawei AppGallery app ID:").to_s.strip
		if appId.empty?
			UI.user_error!("No Huawei AppGallery app ID entered")
		end
		# same request the huawei_appgallery_connect plugin makes to get an access token
		uri = URI("https://connect-api.cloud.huawei.com/api/oauth2/v1/token")
		request = Net::HTTP::Post.new(uri, "Content-Type" => "application/json")
		request.body = { client_id: clientId, client_secret: clientSecret, grant_type: "client_credentials" }.to_json
		begin
			response = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |http| http.request(request) }
		rescue StandardError => e
			UI.user_error!("Huawei AppGallery Connect request failed: #{e.class}")
		end
		json = begin
			JSON.parse(response.body.to_s)
		rescue JSON::ParserError
			{}
		end
		# a rejected client is answered with HTTP 200 and an error in "ret" instead of a token
		if json["access_token"].nil?
			UI.user_error!("Huawei AppGallery Connect rejected the API client ID or secret: " +
				"#{json.dig("ret", "msg") || "HTTP #{response.code}"}")
		end
		{ clientId: clientId, clientSecret: clientSecret, appId: appId }
	end

	desc "GET request to the GitHub API. Unlike fastlane's github_api, it returns the token scopes and never prints the token."
	private_lane :githubGet do |options|
		uri = URI("https://api.github.com#{options[:path]}")
		request = Net::HTTP::Get.new(uri)
		request["Authorization"] = "Bearer #{options[:token]}"
		request["Accept"] = "application/vnd.github+json"
		begin
			response = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |http| http.request(request) }
		rescue StandardError => e
			UI.user_error!("GitHub API request failed: #{e.class}")
		end
		json = begin
			JSON.parse(response.body.to_s)
		rescue JSON::ParserError
			{}
		end
		{ status: response.code.to_i, scopes: response["X-OAuth-Scopes"], json: json }
	end

	desc "Fail if the working tree has changes or untracked files. Unlike ensure_git_status_clean, " +
		"warnings git prints on stderr (e.g. about the untracked cache) are not taken for changes."
	private_lane :ensureCleanWorkingTree do
		changes, warnings, status = Open3.capture3("git", "status", "--porcelain")
		if !status.success?
			UI.user_error!("git status failed: #{warnings.strip}")
		end
		if !changes.strip.empty?
			UI.user_error!("Working tree is not clean, commit, stash or discard these changes first:\n#{changes.rstrip}")
		end
		if !warnings.strip.empty?
			UI.important("git status warned: #{warnings.strip}")
		end
	end

	desc "Check that the branch can be pushed before anything is published: it must not have diverged from its upstream " +
		"branch, and if the upstream branch has newer commits, ask. Returns what pushBranch pushes, and the problems a dry run only warns about."
	private_lane :checkBranchPushable do |options|
		branch, status = Open3.capture2("git", "symbolic-ref", "--quiet", "--short", "HEAD")
		if !status.success?
			UI.user_error!("HEAD is detached, check out the release branch first")
		end
		branch = branch.strip
		remote, remoteStatus = Open3.capture2("git", "config", "branch.#{branch}.remote")
		merge, mergeStatus = Open3.capture2("git", "config", "branch.#{branch}.merge")
		if !remoteStatus.success? || !mergeStatus.success?
			next {
				target: ["--set-upstream", "origin", "HEAD"],
				description: "branch #{branch} to origin, as new upstream branch",
				problems: [],
			}
		end
		remote = remote.strip
		merge = merge.strip
		sh("git", "fetch", "--quiet", remote)
		upstream = sh("git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}", log: false).strip
		unpushed = sh("git", "rev-list", "--count", "#{upstream}..HEAD", log: false).strip.to_i
		missing = sh("git", "rev-list", "--count", "HEAD..#{upstream}", log: false).strip.to_i
		problems = []
		if unpushed > 0 && missing > 0
			problems << "#{branch} and #{upstream} have diverged (#{unpushed} local, #{missing} upstream commit(s)), " +
				"rebase onto or reset to #{upstream} first"
			dryRunOr(dryRun: options[:dryRun], message: problems.last)
		elsif missing > 0
			promptYesNo(
				title: "#{upstream} has newer commits",
				text: "#{missing} commit(s) on #{upstream} are not in the local branch and will not be part of this release.",
				question: "Release without these commits?"
			)
		end
		if !problems.empty?
			# only reached in a dry run, the push would be rejected
			next { target: nil, description: "nothing, #{branch} has diverged from #{upstream}", problems: problems }
		end
		{
			target: unpushed > 0 ? [remote, "HEAD:#{merge}"] : nil,
			description: unpushed > 0 ? "#{unpushed} commit(s) of #{branch} to #{upstream}" : "nothing, #{branch} is pushed",
			problems: problems,
		}
	end

	desc "Push the release branch, as checked by checkBranchPushable, returns a summary"
	private_lane :pushBranch do |options|
		push = options[:push]
		next nil if push[:target].nil?
		if options[:dryRun]
			UI.important("[dry run] would push #{push[:description]}: git push #{push[:target].join(" ")}")
			next "Branch: would push #{push[:description]}"
		end
		sh("git", "push", *push[:target])
		"Branch: pushed #{push[:description]}"
	end

	desc "Fail with the message, or only warn about it in a dry run"
	private_lane :dryRunOr do |options|
		if options[:dryRun]
			UI.important("[dry run] would fail: #{options[:message]}")
		else
			UI.user_error!(options[:message])
		end
	end

	desc "Check that the release artifacts were built from the current commit"
	private_lane :checkArtifactsCommit do
		if !File.exist?("../#{BUILT_COMMIT_PATH}")
			UI.user_error!("#{BUILT_COMMIT_PATH} not found, run releasePhase1 first")
		end
		builtCommit = File.read("../#{BUILT_COMMIT_PATH}").strip
		headCommit = last_git_commit[:commit_hash]
		if builtCommit != headCommit
			UI.user_error!("Artifacts were built from #{builtCommit}, but HEAD is #{headCommit}. Run releasePhase1 again.")
		end
	end

	desc "Create and push release tag, returns a summary. A tag on HEAD from a failed earlier run is reused."
	private_lane :tag do |options|
		tagName = options[:name]
		existing, status = Open3.capture2("git", "rev-parse", "--quiet", "--verify", "refs/tags/#{tagName}^{commit}")
		if status.success?
			if existing.strip != last_git_commit[:commit_hash]
				UI.user_error!("Tag #{tagName} already exists on another commit: #{existing.strip}")
			end
			UI.important("Tag #{tagName} already exists on HEAD, it is reused")
			summary = "Tag: #{tagName} (already existed on HEAD)"
		elsif options[:dryRun]
			UI.important("[dry run] would create signed tag #{tagName} on HEAD")
			summary = "Tag: #{tagName} would be created on HEAD and pushed"
		else
			add_git_tag(
			  tag: tagName,
			  sign: true
			)
			summary = "Tag: #{tagName} created on HEAD"
		end
		if options[:dryRun]
			UI.important("[dry run] would push tag #{tagName}")
		else
			push_git_tags(tag: tagName)
			summary += ", pushed"
		end
		summary
	end

	desc "Upload release artifacts to Google Play, returns a summary"
	private_lane :uploadToPlayStore do |options|
		if options[:dryRun]
			UI.important("[dry run] would upload #{bundlePath(versionCode: options[:versionCode])} to Google Play track #{options[:track]}")
			next "Google Play: bundle would be uploaded to track #{options[:track]}"
		end
		upload_to_play_store(
			skip_upload_images: true,
			skip_upload_apk: true,
			track: options[:track],
			aab: bundlePath(versionCode: options[:versionCode])
		)
		"Google Play: bundle uploaded to track #{options[:track]}"
	end

	desc "Check if release artifacts exist"
	private_lane :checkArtifactsExist do |options|
		bundle = bundlePath(versionCode: options[:versionCode])
		if !File.exist?("../#{bundle}")
			UI.user_error!("Bundle not found at #{bundle}, run releasePhase1 first")
		end
	end

	desc "Show versions and prompt for confirmation"
	private_lane :preReleaseChecks do |options|
		versionInfo = options[:versionInfo]
		tagName = options[:tagName]
		currentBranch = git_branch()
		text = "Version name: #{versionInfo["versionName"]}\n" +
				"Version code: #{versionInfo["versionCode"]}\n" +
				"Current branch: #{currentBranch}\n" +
				"Push: #{options[:push][:description]}\n" +
				"Version type: #{options[:type]}\n" +
				"Tag: #{tagName}\n" +
				"Track: #{options[:track]}\n" +
				"Prerelease: #{options[:isPrerelease]}"
		if options[:dryRun]
			promptYesNo(title: "Check the release (dry run, nothing is published)", text: text,
				question: "Run the dry run for this release?")
		else
			promptYesNo(title: "Check the release, it is published after this question", text: text,
				question: "Publish #{versionInfo["versionName"]} now (push, tag, GitHub release, store uploads)?")
		end
	end

	desc "Create GitHub release, returns a summary. A release from a failed earlier run is kept."
	private_lane :createGithubRelease do |options|
		existing = githubGet(token: options[:token], path: "/repos/#{REPO}/releases/tags/#{options[:tag]}")
		case existing[:status]
		when 200
			UI.important("GitHub release for #{options[:tag]} already exists, it is not changed: #{existing[:json]["html_url"]}")
			"GitHub release: #{existing[:json]["html_url"]} (already existed, not changed)"
		when 404
			if options[:dryRun]
				assets = Dir.glob(File.expand_path("../release/*.apk")).map { |path| File.basename(path) }
				UI.important("[dry run] would create GitHub release \"#{options[:name]}\" for tag #{options[:tag]}, " +
					"prerelease: #{options[:isPrerelease]}, assets: #{assets.empty? ? "none" : assets.join(", ")}")
				next "GitHub release: \"#{options[:name]}\" would be created with #{assets.length} APK(s)"
			end
			set_github_release(
				repository_name: REPO,
				api_token: options[:token],
				name: options[:name],
				is_prerelease: options[:isPrerelease],
				tag_name: options[:tag],
				description: File.read(changelogPath(versionCode: options[:versionCode]), encoding: "UTF-8"),
				upload_assets: Dir.glob(File.expand_path("../release/*.apk"))
			)
			"GitHub release: #{lane_context[SharedValues::SET_GITHUB_RELEASE_HTML_LINK]}"
		else
			UI.user_error!("Could not check for an existing GitHub release (HTTP #{existing[:status]})")
		end
	end

	desc "Whether the release is uploaded to Huawei AppGallery: final releases of Files"
	private_lane :uploadsToHuawei do |options|
		APP == "files" && options[:isPrerelease] == false
	end

	desc "Upload the Huawei APK to Huawei AppGallery, if credentials were asked for"
	private_lane :uploadToHuawei do |options|
		credentials = options[:credentials]
		next nil if credentials.nil?
		apk = "release/nextcloud-#{APP}-huawei-#{options[:versionCode]}.apk"
		if options[:dryRun]
			UI.important("[dry run] would upload #{apk} to Huawei AppGallery")
			next "Huawei AppGallery: APK would be uploaded and submitted for review"
		end
		huawei_appgallery_connect(
			client_id: credentials[:clientId],
			client_secret: credentials[:clientSecret],
			app_id: credentials[:appId],
			apk_path: apk,
			submit_for_review: true,
			delay_before_submit_for_review: 30,
		)
		"Huawei AppGallery: APK uploaded and submitted for review"
	end
end

desc "Show what to check and ask a yes/no question, abort on no"
private_lane :promptYesNo do |options|
    puts("")
    UI.header(options[:title] || "Check")
    # leading line break, so all lines of the text start at the beginning of a line, after fastlane's timestamp line
    puts("\n" + options[:text])
    puts("")
    # UI.confirm instead of the prompt action, whose step header would separate the question from the text
    if !UI.confirm(options[:question] || "Is this okay?")
        UI.user_error!("Aborting")
    end
end

private_lane :promptSecret do |options|
    value = prompt(text: options[:text], secure_text: true).to_s
    if value.strip.empty?
        UI.user_error!("No value entered for: #{options[:text]}")
    end
    value
end

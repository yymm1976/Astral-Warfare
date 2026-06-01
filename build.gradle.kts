plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.139"
    id("idea")
    id("eclipse")
}

val parchment_minecraft_version : String by project
val parchment_mappings_version  : String by project
val minecraft_version           : String by project
val minecraft_version_range     : String by project
val neo_version                 : String by project
val neo_version_range           : String by project
val loader_version_range        : String by project
val mod_id                      : String by project
val mod_name                    : String by project
val mod_license                 : String by project
val mod_version                 : String by project
val mod_group_id                : String by project

tasks.wrapper.configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

base {
    archivesName.set("${mod_id}-${minecraft_version}")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

neoForge {
    version = neo_version

    // 启用 AT 验证：如果 AT 目标不存在或格式错误，编译时立即报错
    validateAccessTransformers = true

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            server()
            gameDirectory = file("run-server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run-server")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            data()
            gameDirectory = file("run-data")
            programArguments.addAll(
                "--mod",
                mod_id,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

val localRuntime: Configuration by configurations.creating
configurations {
    runtimeClasspath {
        extendsFrom(localRuntime)
    }
}

repositories {
    // Lodestone 依赖：通过 CurseMaven 获取 NeoForge 1.21.1 版本
    // BlameJared Maven 仅包含 1.18.2 版本，不包含 1.21.1 NeoForge 版本
    // Modrinth Maven 的版本过旧（1.7.0），缺少关键 API
    // CurseMaven 坐标：curse.maven:lodestone-{project_id}:{file_id}
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    // Curios API（Lodestone NeoForge 版本的运行时依赖）
    maven {
        name = "CuriosMaven"
        url = uri("https://maven.theillusivec4.top/")
    }
    // PAL（Player Animation Library）：玩家动画系统
    maven {
        name = "RedlanceMinecraft"
        url = uri("https://repo.redlance.org/public")
    }
}

dependencies {
    // Lodestone 1.21.1 NeoForge：粒子系统 + 后处理着色器
    // CurseMaven 坐标：curse.maven:lodestone-616457:{file_id}
    // file_id 7264731 对应 lodestone-1.21.1-1.7.0.268.jar（NeoForge 版本）
    implementation("curse.maven:lodestone-616457:7264731")
    // Curios API：Lodestone NeoForge 版本的硬依赖
    compileOnly("top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api")
    runtimeOnly("top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1")
    // PAL（Player Animation Library）：玩家动画系统
    // 官方坐标：com.zigythebird.playeranim:PlayerAnimationLibNeo
    implementation("com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.4+mc.1.21.1")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("generateModMetadata")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version"       to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version"             to neo_version,
        "neo_version_range"       to neo_version_range,
        "loader_version_range"    to loader_version_range,
        "mod_id"                  to mod_id,
        "mod_name"                to mod_name,
        "mod_license"             to mod_license,
        "mod_version"             to mod_version,
    )
    inputs.properties(replaceProperties)

    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.compileJava {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

#!/usr/bin/env python3
"""Generates Barajyq.xcodeproj/project.pbxproj (no Xcode / XcodeGen needed).
Run from ios/:  python3 tools/gen_xcodeproj.py
Alternatively use XcodeGen with project.yml:  xcodegen generate
"""
import hashlib, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'Barajyq'
LANGS = ['en', 'ru', 'kk']

def uid(*parts):
    return hashlib.md5('/'.join(parts).encode()).hexdigest()[:24].upper()

def q(s):
    s = str(s)
    if s and all(c.isalnum() or c in '._/$' for c in s) and not s.startswith('$('):
        return s
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

objs = {}   # id -> (isa, dict)
def add(i, isa, **kw):
    objs[i] = (isa, kw)
    return i

groups = {}  # rel dir -> children list
def group_children(rel):
    return groups.setdefault(rel, [])

swift_files = sorted(p.relative_to(SRC) for p in SRC.rglob('*.swift'))
build_sources, build_resources = [], []

for rel in swift_files:
    fr = add(uid('fr', str(rel)), 'PBXFileReference', lastKnownFileType='sourcecode.swift', path=rel.name, sourceTree='"<group>"')
    group_children(str(rel.parent)).append(fr)
    build_sources.append(add(uid('bf', str(rel)), 'PBXBuildFile', fileRef=fr))

def plain_file(rel, ftype, resource=True):
    fr = add(uid('fr', rel), 'PBXFileReference', lastKnownFileType=ftype, path=pathlib.Path(rel).name, sourceTree='"<group>"')
    group_children(str(pathlib.Path(rel).parent)).append(fr)
    if resource:
        build_resources.append(add(uid('bf', rel), 'PBXBuildFile', fileRef=fr))
    return fr

plain_file('Resources/Assets.xcassets', 'folder.assetcatalog')
plain_file('Resources/PrivacyInfo.xcprivacy', 'text.xml')
plain_file('Resources/events.json', 'text.json')  # bundled feed copy (offline / first start)
plain_file('Resources/Info.plist', 'text.plist.xml', resource=False)
plain_file('Resources/Barajyq.entitlements', 'text.plist.entitlements', resource=False)
xcconfig = plain_file('Config/Google.xcconfig', 'text.xcconfig', resource=False)

for name in ['Localizable.strings', 'InfoPlist.strings']:
    kids = [add(uid('fr', l, name), 'PBXFileReference', lastKnownFileType='text.plist.strings', name=l,
                path=f'{l}.lproj/{name}', sourceTree='"<group>"') for l in LANGS]
    vg = add(uid('vg', name), 'PBXVariantGroup', children=kids, name=name, sourceTree='"<group>"')
    group_children('Resources').append(vg)
    build_resources.append(add(uid('bf', name), 'PBXBuildFile', fileRef=vg))

# group tree
def make_group(rel):
    name = pathlib.Path(rel).name
    children = list(group_children(rel))
    subdirs = sorted({r for r in groups if r != rel and str(pathlib.Path(r).parent) == rel})
    sub_ids = [make_group(s) for s in subdirs]
    return add(uid('grp', rel), 'PBXGroup', children=sub_ids + children, path=name, sourceTree='"<group>"')

top_dirs = sorted({r.split('/')[0] for r in groups if r != '.'})
for d in top_dirs:  # ensure parents exist
    group_children(d)
barajyq_group = add(uid('grp', 'Barajyq'), 'PBXGroup',
                   children=[make_group(d) for d in top_dirs], path='Barajyq', sourceTree='"<group>"')

product = add(uid('product'), 'PBXFileReference', explicitFileType='wrapper.application', includeInIndex='0',
              path='Barajyq.app', sourceTree='BUILT_PRODUCTS_DIR')
products_group = add(uid('grp', 'Products'), 'PBXGroup', children=[product], name='Products', sourceTree='"<group>"')
main_group = add(uid('grp', 'main'), 'PBXGroup', children=[barajyq_group, products_group], sourceTree='"<group>"')

# Swift package: GoogleSignIn-iOS
pkg = add(uid('pkg'), 'XCRemoteSwiftPackageReference',
          repositoryURL='"https://github.com/google/GoogleSignIn-iOS"',
          requirement={'kind': 'upToNextMajorVersion', 'minimumVersion': '8.0.0'})
pkg_prod = add(uid('pkgprod'), 'XCSwiftPackageProductDependency', package=pkg, productName='GoogleSignIn')
pkg_bf = add(uid('bf', 'GoogleSignIn'), 'PBXBuildFile', productRef=pkg_prod)

src_phase = add(uid('phase', 'src'), 'PBXSourcesBuildPhase', buildActionMask='2147483647', files=build_sources,
                runOnlyForDeploymentPostprocessing='0')
res_phase = add(uid('phase', 'res'), 'PBXResourcesBuildPhase', buildActionMask='2147483647', files=build_resources,
                runOnlyForDeploymentPostprocessing='0')
fw_phase = add(uid('phase', 'fw'), 'PBXFrameworksBuildPhase', buildActionMask='2147483647', files=[pkg_bf],
               runOnlyForDeploymentPostprocessing='0')

common_proj = {
    'ALWAYS_SEARCH_USER_PATHS': 'NO', 'CLANG_ENABLE_MODULES': 'YES', 'CLANG_ENABLE_OBJC_ARC': 'YES',
    'ENABLE_STRICT_OBJC_MSGSEND': 'YES', 'GCC_NO_COMMON_BLOCKS': 'YES',
    'IPHONEOS_DEPLOYMENT_TARGET': '16.0', 'SDKROOT': 'iphoneos', 'SWIFT_VERSION': '5.0',
    'LOCALIZATION_PREFERS_STRING_CATALOGS': 'NO',
}
debug_proj = dict(common_proj, DEBUG_INFORMATION_FORMAT='dwarf', ENABLE_TESTABILITY='YES', GCC_OPTIMIZATION_LEVEL='0',
                  ONLY_ACTIVE_ARCH='YES', SWIFT_OPTIMIZATION_LEVEL='"-Onone"',
                  SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG', MTL_ENABLE_DEBUG_INFO='INCLUDE_SOURCE')
release_proj = dict(common_proj, DEBUG_INFORMATION_FORMAT='"dwarf-with-dsym"', ENABLE_NS_ASSERTIONS='NO',
                    SWIFT_COMPILATION_MODE='wholemodule', SWIFT_OPTIMIZATION_LEVEL='"-O"', VALIDATE_PRODUCT='YES')
target_settings = {
    'ASSETCATALOG_COMPILER_APPICON_NAME': 'AppIcon',
    'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME': 'AccentColor',
    'CODE_SIGN_ENTITLEMENTS': 'Barajyq/Resources/Barajyq.entitlements',
    'CODE_SIGN_STYLE': 'Automatic',
    'CURRENT_PROJECT_VERSION': '"$(CURRENT_PROJECT_VERSION)"',
    'ENABLE_PREVIEWS': 'YES',
    'GENERATE_INFOPLIST_FILE': 'NO',
    'INFOPLIST_FILE': 'Barajyq/Resources/Info.plist',
    'LD_RUNPATH_SEARCH_PATHS': '("$(inherited)", "@executable_path/Frameworks")',
    'PRODUCT_NAME': '"$(TARGET_NAME)"',
    'SUPPORTED_PLATFORMS': '"iphoneos iphonesimulator"',
    'SUPPORTS_MACCATALYST': 'NO',
    'SWIFT_EMIT_LOC_STRINGS': 'YES',
    'TARGETED_DEVICE_FAMILY': '"1,2"',
}
del target_settings['CURRENT_PROJECT_VERSION']  # comes from xcconfig

def cfg(name, settings, base=None):
    kw = {'buildSettings': settings, 'name': name}
    if base: kw['baseConfigurationReference'] = base
    return add(uid('cfg', name, str(base)), 'XCBuildConfiguration', **kw)

proj_cfgs = add(uid('cfglist', 'proj'), 'XCConfigurationList',
                buildConfigurations=[cfg('Debug', debug_proj), cfg('Release', release_proj)],
                defaultConfigurationIsVisible='0', defaultConfigurationName='Release')
tgt_cfgs = add(uid('cfglist', 'tgt'), 'XCConfigurationList',
               buildConfigurations=[cfg('Debug', target_settings, xcconfig), cfg('Release', target_settings, xcconfig)],
               defaultConfigurationIsVisible='0', defaultConfigurationName='Release')

target = add(uid('target'), 'PBXNativeTarget', buildConfigurationList=tgt_cfgs,
             buildPhases=[src_phase, fw_phase, res_phase], buildRules=[], dependencies=[], name='Barajyq',
             packageProductDependencies=[pkg_prod], productName='Barajyq', productReference=product,
             productType='"com.apple.product-type.application"')
project = add(uid('project'), 'PBXProject',
              attributes={'BuildIndependentTargetsInParallel': '1', 'LastSwiftUpdateCheck': '1600',
                          'LastUpgradeCheck': '1600', 'TargetAttributes': {target: {'CreatedOnToolsVersion': '16.0'}}},
              buildConfigurationList=proj_cfgs, compatibilityVersion='"Xcode 14.0"', developmentRegion='ru',
              hasScannedForEncodings='0', knownRegions=['en', 'Base', 'ru', 'kk'], mainGroup=main_group,
              packageReferences=[pkg], productRefGroup=products_group, projectDirPath='""', projectRoot='""',
              targets=[target])

def fmt(v, ind):
    pad = '\t' * ind
    if isinstance(v, dict):
        inner = ''.join(f'{pad}\t{q(k) if not k.isupper() or len(k) != 24 else k} = {fmt(x, ind + 1)};\n' for k, x in v.items())
        return '{\n' + inner + pad + '}'
    if isinstance(v, list):
        return '(\n' + ''.join(f'{pad}\t{fmt(x, ind + 1)},\n' for x in v) + pad + ')'
    s = str(v)
    if s.startswith('"') or s.startswith('('):
        return s
    return q(s)

out = ['// !$*UTF8*$!', '{', '\tarchiveVersion = 1;', '\tclasses = {', '\t};', '\tobjectVersion = 56;', '\tobjects = {', '']
for isa in sorted({o[0] for o in objs.values()}):
    out.append(f'/* Begin {isa} section */')
    for i, (t, kw) in sorted(objs.items()):
        if t != isa: continue
        body = {'isa': isa, **kw}
        out.append(f'\t\t{i} = {fmt(body, 2)};')
    out.append(f'/* End {isa} section */\n')
out += ['\t};', f'\trootObject = {project};', '}', '']
proj_dir = ROOT / 'Barajyq.xcodeproj'
proj_dir.mkdir(exist_ok=True)
(proj_dir / 'project.pbxproj').write_text('\n'.join(out))
# shared scheme
sd = proj_dir / 'xcshareddata' / 'xcschemes'; sd.mkdir(parents=True, exist_ok=True)
(sd / 'Barajyq.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1600" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{target}" BuildableName = "Barajyq.app" BlueprintName = "Barajyq" ReferencedContainer = "container:Barajyq.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv = "YES">
   </TestAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{target}" BuildableName = "Barajyq.app" BlueprintName = "Barajyq" ReferencedContainer = "container:Barajyq.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
''')
print('ok', len(objs), 'objects,', len(build_sources), 'swift files')

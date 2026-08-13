#define AppName "VRCM"
#define AppVersion "1.1.1"
#define SourceDir "..\composeApp\build\compose\binaries\main-release\app\VRCM"

[Setup]
AppId={{AEBFB803-0655-4C7E-8C79-F29E14618397}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=VRCM Team
AppPublisherURL=https://github.com/vrcm-team/VRCM
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\composeApp\build\installer
OutputBaseFilename=VRCM-{#AppVersion}-setup
SetupIconFile=..\composeApp\src\desktopMain\resources\VRCM.ico
UninstallDisplayIcon={app}\VRCM.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "chinesesimplified"; MessagesFile: "ChineseSimplified.isl"
Name: "chinesetraditional"; MessagesFile: "ChineseTraditional.isl"
Name: "japanese"; MessagesFile: "Japanese.isl"

[CustomMessages]
english.CreateDesktopShortcut=Create a desktop shortcut
chinesesimplified.CreateDesktopShortcut=创建桌面快捷方式
chinesetraditional.CreateDesktopShortcut=建立桌面捷徑
japanese.CreateDesktopShortcut=デスクトップショートカットを作成
english.AdditionalShortcuts=Additional shortcuts:
chinesesimplified.AdditionalShortcuts=附加快捷方式：
chinesetraditional.AdditionalShortcuts=其他捷徑：
japanese.AdditionalShortcuts=追加のショートカット：
english.LaunchVrcm=Launch VRCM
chinesesimplified.LaunchVrcm=启动 VRCM
chinesetraditional.LaunchVrcm=啟動 VRCM
japanese.LaunchVrcm=VRCM を起動

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopShortcut}"; GroupDescription: "{cm:AdditionalShortcuts}"

[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\VRCM"; Filename: "{app}\VRCM.exe"
Name: "{autodesktop}\VRCM"; Filename: "{app}\VRCM.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\VRCM.exe"; Description: "{cm:LaunchVrcm}"; Flags: nowait postinstall skipifsilent

; Script Inno Setup para o instalador oficial do ChaveiroCar Windows Desktop
#define MyAppName "ChaveiroCar"
#define MyAppVersion "1.15.0"
#define MyAppPublisher "ChaveiroCar"
#define MyAppExeName "ChaveiroCar.Windows.exe"

[Setup]
AppId={{C782E9B1-4A56-4D2A-98C3-FA0E5B108922}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\..\dist
OutputBaseFilename=ChaveiroCar-Setup
SetupIconFile=..\ChaveiroCar.Windows\app.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
CloseApplications=yes
RestartApplications=no
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\ChaveiroCar.Windows\publish\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
procedure SHChangeNotify(wEventId: Longint; uFlags: Cardinal; dwItem1, dwItem2: Longint);
  external 'SHChangeNotify@shell32.dll stdcall';

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    // Atualiza imediatamente o cache de ícones da barra de tarefas e atalhos do Windows Explorer.
    SHChangeNotify($08000000, $0000, 0, 0);
  end;
end;

// MIT License
//
// Copyright (c) 2023-Present - Violet Hansen - (aka HotCakeX on GitHub) - Email Address: spynetgirl@outlook.com
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// See here for more information: https://github.com/HotCakeX/Harden-Windows-Security/blob/main/LICENSE
//

using System.Collections.Generic;

namespace AppControlManager.SiPolicy;

internal sealed class AppManifest(List<SettingDefinition> settingDefinition, string id)
{
	internal List<SettingDefinition> SettingDefinition => settingDefinition;

	internal string Id => id;
}

internal sealed class SettingDefinition(string name, SettingType type, bool ignoreAuditPolicies)
{
	internal string Name => name;

	internal SettingType Type => type;

	internal bool IgnoreAuditPolicies => ignoreAuditPolicies;
}

internal enum SettingType
{
	Bool,
	StringList,
	StringSet,
}

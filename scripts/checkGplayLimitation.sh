#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
# SPDX-License-Identifier: GPL-3.0-or-later
#

result=""

for log in fastlane/metadata/android/*/changelogs/*
    do
    if [[ -e $log && $(wc -m $log | cut -d" " -f1) -gt 500 ]]
        then
        result=$log"<br>"$result
    fi
done

echo -e "$result";

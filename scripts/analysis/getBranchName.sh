#!/bin/bash

# SPDX-FileCopyrightText: 2017-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2017 Tobias Kaminsky <tobias@kaminsky.me>
# SPDX-License-Identifier: GPL-3.0-or-later

# $1: username, $2: password/token, $3: pull request number

if [ -z "$3" ] ; then
    git branch | grep '\*' | cut -d' ' -f2
else
    curl 2>/dev/null -u "$1":"$2" "https://api.github.com/repos/nextcloud/talk-android/pulls/$3" | jq .head.ref
fi

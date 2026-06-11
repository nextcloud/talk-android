#!/bin/sh

# Nextcloud Android Library
#
# SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
# SPDX-License-Identifier: MIT
#

SERVER_VERSION_MASTER=$1

php /var/www/html/occ log:manage --level warning

OC_PASS=user1 php /var/www/html/occ user:add --password-from-env --display-name='User One' user1
OC_PASS=user2 php /var/www/html/occ user:add --password-from-env --display-name='User Two' user2
OC_PASS=user3 php /var/www/html/occ user:add --password-from-env --display-name='User Three' user3
OC_PASS=test php /var/www/html/occ user:add --password-from-env --display-name='Test@Test' test@test
OC_PASS=test php /var/www/html/occ user:add --password-from-env --display-name='Test Spaces' 'test test'
php /var/www/html/occ user:setting user2 files quota 1G
php /var/www/html/occ group:add users
php /var/www/html/occ group:adduser users user1
php /var/www/html/occ group:adduser users user2
php /var/www/html/occ group:adduser users test

git clone --depth=1 -b $SERVER_VERSION_MASTER https://github.com/nextcloud/notifications.git /var/www/html/apps/notifications/
cd /var/www/html/apps/notifications; composer install --no-dev
php /var/www/html/occ app:enable -f notifications
php /var/www/html/occ notification:generate test -d test

php /var/www/html/occ app:enable -f testing

git clone --depth=1 -b $SERVER_VERSION_MASTER https://github.com/nextcloud/spreed.git /var/www/html/apps/spreed/
php /var/www/html/occ app:enable -f spreed

php /var/www/html/occ config:system:set ratelimit.protection.enabled --value false --type bool

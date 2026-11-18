# Discord Data.db

Enable Discord on one Paper backend and keep its configuration and token. UltimateShop creates `plugins/UltimateShop/Data.db` automatically. Players redeem Discord `/link` codes on that backend.

This file stores the bot's links, entries, draws, and unsent prizes. The website shop and in-game prize delivery still use the shared MariaDB database. Prizes that cannot reach MariaDB remain queued in Data.db for retry.

Discord links and active giveaways from the previous MariaDB-backed bot are not imported. Finish any existing giveaway before upgrading. Previously queued shop deliveries remain in MariaDB.

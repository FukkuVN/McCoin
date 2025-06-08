# FukkuCoinPlugin

This is a Minecraft plugin for managing a virtual currency called FukkuCoin.

## Features

- Virtual currency system with balance management
- NPC shops with GUI for buying items using FukkuCoin
- Daily rewards command to claim FukkuCoin
- Admin commands to add or remove money from players
- Configuration reload command
- NPC management commands for creating and moving NPC shops

## Commands

| Command                                   | Purpose                            | Description                                      |
| -----------------------------------------| --------------------------------- | ------------------------------------------------|
| `/fukkucoin shop`                        | Open virtual currency shop        | Opens the shop GUI showing player's balance     |
| `/fukkucoin points`                      | View accumulated points or money  | Displays player's current points or money        |
| `/fukkucoin daily`                       | Daily reward claim                 | Allows players to claim daily FukkuCoin rewards  |
| `/fukkucoin addmoney <player> <amount>` | Admin: Add money to player        | Admin command to add money to a player's account |
| `/fukkucoin removemoney <player> <amount>` | Admin: Remove money from player  | Admin command to subtract money from a player    |
| `/fukkucoin reload`                      | Reload plugin configuration       | Reloads config files like npcshops.yml, config.yml |
| `/fukkucoin npc create <name>`           | Create new NPC shop               | Creates an NPC to open shop or daily rewards     |
| `/fukkucoin npc move <npcID> <x> <y> <z>` | Move NPC to new coordinates      | Moves an NPC to specified coordinates            |

## Configuration Files

- `npcshops.yml`: Defines NPC shops and their items
- `config.yml`: General plugin configuration such as currency name
- `balance.yml`: Stores player balances (auto-generated)
- `npcshops.yml`: Stores NPC shop data (auto-generated)

## Build

The project supports both Maven and Gradle build systems.

- Maven: Use `mvn clean package` to build the plugin jar
- Gradle: Use `gradle build` to build the plugin jar

## Installation

1. Build the plugin jar using Maven or Gradle
2. Place the jar file in the `plugins` folder of your Minecraft server
3. Start the server to load the plugin
4. Configure NPC shops and currency in the config files as needed

## License

Specify your license here.

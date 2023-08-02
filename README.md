# AioPlugin
A fabric mod which contains these commands and functions:
#### /tpa(and /tpah) command
    Usage:
      /tpa <PlayerName>                             Send a teleport request to the player. Perform directly teleport if target is fake player
      /tpa attitude accept/reject <PlayerName>      Accept or reject target player's teleport request
      /tpah <PlayerName>                            Send a "teleport to here" request
      /tpa list [<page>]                            List all teleport requests
      /tpa crd <pos> [<dimension>]                  Teleport to specified position (Can be disabled using command: /tpa settings enableTpaCrd false)

  #### /back command
    Usage:
      /back                                         Return to the latest recorded location
      /back list [page]                             List recorded location
      /back slot <slot>                             Return to the specified location
      /back settings
            enableAutoRecord           true/false   Enable autoRecord or not
            enableRecordNotification   true/false   Enable notification or not
            
      
## Dependencies
  Minecraft       (version=1.20 and 1.20.1)\
  [FabricLoader](https://fabricmc.net/use/installer/)    (version>=0.14.21)\
  [FabricAPI](https://www.curseforge.com/minecraft/mc-mods/fabric-api)       (Any compatible version)\
  [Fabric-Carpet](https://github.com/gnembon/fabric-carpet)   (>=1.4.112)

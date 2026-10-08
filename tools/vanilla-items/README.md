# Исходные ванильные текстуры

Файлы извлечены без изменения из официального Minecraft Java 26.2 client.jar. PNG 16×16 используются генератором для определения металлических областей инструментов и для предпросмотра. Игровой ресурспак ссылается на оригинальные `minecraft:item/iron_*` , `minecraft:item/golden_*` и `minecraft:item/copper_*`, встроенные в клиент.

- Метаданные версий: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- Архив клиента: https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar
- Проверенный SHA-1: `2dc72797acbc1b63fc16a11c4ac393605f453754`
- Формат `minecraft:item_model` и `minecraft:constant` tint: https://www.minecraft.net/fr-fr/article/minecraft-java-edition-1-21-4

`BuildPack.java` создаёт нативные JSON-модели: металлические полосы с исходными UV-координатами для инструментов, обычный `item/generated` для брони, три цветовых определения на предмет. Деревянные пиксели и отдельный набалдашник меча не включаются в геометрию. Готовые изделия не переопределяются.

Оригинальная графика Minecraft принадлежит Mojang/Microsoft.

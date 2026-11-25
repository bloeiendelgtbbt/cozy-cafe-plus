# Cozy Cafe+

Fabric-форк мода [Cozy Cafe](https://github.com/Chakyl/Cozy-Cafe) (Nitbe и Chakyl) с упором на мультиплеер:
своё заведение на сервере, официанты, гости-игроки и НПС, меню с ценами, счёт, оценки и долги.
Симулятор работы из реальной жизни. Английский и русский языки.

*A Fabric fork of [Cozy Cafe](https://github.com/Chakyl/Cozy-Cafe) by Nitbe and Chakyl, built for multiplayer:
run a cafe on a server with waiters, player and NPC guests, a priced menu, bills, ratings and debts.
In English and Russian.*

## Возможности

- **Заведение = приват Privit.** Один планшет заведения на приват. Владелец нанимает официантов,
  обслуживать можно только в фартуке.
- **Меню из любых предметов** с ценами в выбранной владельцем валюте. Настройки, касса, список должников, рейтинг.
- **Гости-игроки** садятся за столик и заказывают сами. Заказ можно подавать частями и отменять.
  Таймеры ожидания, счёт с оплатой и оценкой от 1 до 5 звёзд. Ушёл, не заплатив, — появился долг.
- **НПС-гости** ходят как жители, открывают двери заведения, делают заказ и платят.
- **Отчёт о смене** владельцу при закрытии, вывеска OPEN / CLOSED, метки для Xaero's Minimap.

## Зависимости

| Мод | |
|---|---|
| Minecraft 1.20.1, Fabric Loader ≥ 0.15, Fabric API | обязательно |
| [Privit](https://modrinth.com/mod/privit) 1.4.6+ | обязательно (приваты) |
| [Sit](https://modrinth.com/mod/bl4cks-sit) + Cloth Config | обязательно |
| Trinkets | по желанию (правила фартука для аксессуаров) |
| Xaero's Minimap | по желанию (метки заведений) |

## Сборка

```
./gradlew build          # jar в build/libs
./gradlew runGametest    # автотесты
```

Зависимости скачиваются из Modrinth Maven, Ladysnake Maven и Maven Central.

## Как сделан мод

Мод разработан bloeiende с помощью ИИ (Claude): идея, игровые правила, решения по механикам и тестирование — bloeiende,
большая часть кода написана с ИИ. Основан на Cozy Cafe от Nitbe и Chakyl.

*Developed by bloeiende with AI assistance (Claude). Game design, testing and direction by bloeiende.
Based on Cozy Cafe by Nitbe and Chakyl.*

## Связь

bloeiende@icloud.com

## Лицензия

GPL-3.0, как и у оригинала (см. [LICENSE.txt](LICENSE.txt)). Что взято из оригинала и что изменено — в [CREDITS.txt](CREDITS.txt).

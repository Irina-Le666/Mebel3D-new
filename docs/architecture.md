# Архитектура «Мебель 3D»

## Предлагаемый стек
- Kotlin + Android Jetpack Compose для оболочки и форм управления.
- Android View/Surface для интерактивного 3D-рендера; сравнить Filament/SceneView с собственным OpenGL ES-слоем на раннем техническом spike, выбрать после проверки касаний, размеров сцены и сборки на целевом Android.
- Room для индексации проектов и метаданных; версионируемый JSON-файл проекта для геометрии, объектов, материалов, фото-ссылок и истории изменений.
- Единицы хранения — метры в ядре (Float/Double с правилами точности), пользовательские поля — миллиметры; геометрические расчёты выполнять в Double, переводить в float только на границе рендера.
- PDF/SVG для чертежей на первом этапе; DXF как отдельный экспортный этап после проверки совместимости.

## Модули
1. `project-model`: проект, помещения, стены, проёмы, мебель, детали, материалы, инженерные элементы.
2. `geometry-core`: преобразования, привязки, пересечения, проверки столкновений и ограничений, вычисление габаритов.
3. `plan-2d`: масштабируемый план, размерные линии, выбор/перемещение/поворот.
4. `scene-3d`: камера, изометрия, управление стенами, освещение и выделение объектов.
5. `catalog`: категории, поиск, фильтры, шаблоны мебели и примитивы.
6. `furniture-editor`: панели, полки, фасады, перегородки, ящики и параметрические связи.
7. `materials`: цвета, текстуры, назначение материалов.
8. `engineering`: окна, двери, розетки, трубы, выводы воды/газа и препятствия.
9. `photo-survey`: фото, калибровка, привязка к стене, ручная разметка; автоматическое восстановление — отдельный исследовательский этап.
10. `drawings`: развёртки, деталировка, ведомости и экспорт.
11. `storage`: автосохранение, восстановление, версии и отмена/повтор.

## Основные инварианты
- Один источник геометрии для 2D, 3D, размерных линий и чертежей.
- Размеры не извлекаются из пикселей рендера.
- У каждого объекта стабильный ID и локальная система координат.
- Команды изменения модели обратимы для Undo/Redo.
- Фото, AI-предложение и ручная разметка не считаются точным обмером без пользовательской калибровки.
- Валидация проекта не должна молча менять пользовательские размеры.

## Критерии первого вертикального среза
- Создать помещение заданных размеров.
- Построить стены в 2D и 3D из одной модели.
- Добавить дверь и окно с позициями на стенах.
- Добавить прямоугольный мебельный объект.
- Выбрать объект, изменить W/H/D, координаты и угол; изменения видны в плане и 3D.
- Undo/Redo и сохранение/восстановление проекта.
- Автотесты геометрии и smoke test на реальном Android.

## Реализованный срез 1 — геометрическая модель

Исходники: `android/app/src/main/java/ru/mebel3d/app/core/Geometry.kt`.

- Все линейные размеры хранятся в целых миллиметрах.
- `RoomModel` задаёт габариты, высоту и толщину стен; проёмы привязаны к стороне стены, смещению и высотным параметрам.
- `FurnitureItem` хранит габариты, координаты, угол поворота, материал и цвет.
- `FurnitureAssembly` и `FurniturePart` задают конструкцию из самостоятельных компонентов.
- `GeometryEngine` содержит поворот габаритного прямоугольника, проверки границ/высоты/коллизий и базовую генерацию деталей шкафа.
- `RoomCanvas.kt` визуализирует ту же модель в плановом и изометрическом режимах.

Ограничения текущего среза: пересечения проверяются по повёрнутым AABB-габаритам (консервативная проверка, не точная SAT-геометрия); визуализация 3D — облегчённая изометрическая отрисовка Compose Canvas, а не полноценный PBR-рендерер; проёмы уже есть в модели, но пока не выведены в редактор сцены. Эти ограничения должны быть заменены/расширены в последующих модулях.


## Реализованный срез 2 — базовое взаимодействие и хранение

- `RoomCanvas.kt` рисует помещение в плановом и изометрическом представлении и поддерживает выбор предмета касанием.
- Главный экран добавляет предметы из каталога, применяет размеры/координаты/поворот к модели, дублирует и удаляет предметы.
- `ProjectStorage.kt` сохраняет комнату, проёмы и мебель в локальное хранилище Android в JSON с `schemaVersion`.
- Восстановление последнего сохранённого проекта выполняется при открытии приложения. Это локальный черновой механизм, не замена будущему менеджеру проектов/экспорту файлов.

## Чертёжная геометрия и производство (этап 2026-11-08)

- `GeometryEngine.wallSegments(room, side)` формирует прямоугольные участки стены вокруг проёмов. Эти сегменты предназначены для общей геометрии 2D-плана, 3D-стены и фасадной развёртки.
- `GeometryEngine.snap(valueMm, gridMm)` задаёт точную привязку координат к пользовательской сетке в миллиметрах.
- `GeometryEngine.cutList(assembly)` формирует ведомость деталей мебельной сборки с размерами заготовки и идентификатором материала.
- `GeometryEngine.validateAssembly(assembly)` проверяет пустые сборки и дублирующиеся идентификаторы компонентов.
- `ProjectStorage` schema 3 сериализует мебельные сборки и их детали вместе с проектом. Android-интеграционная проверка ещё требуется.

## Приоритет продукта — уточнение 2026-11-09

Основной продукт — визуальный конструктор помещений и интерьеров. Архитектурные приоритеты: интерактивная 3D-сцена и обзор четырёх стен; создание комнаты по размерам и фото с ручной калибровкой; сенсорное размещение/редактирование готовой мебели; каталог мебели, отделок, материалов и света; конструктор мебели как часть интерьера; сохранение проектов и вариантов. Чертежи и спецификации могут оставаться второстепенным экспортом. Производственные расчёты, раскрой и технологические рекомендации не должны определять архитектуру, интерфейс или порядок разработки.


## Отделка поверхностей помещения

`RoomModel.wallMaterialId` и `floorMaterialId` ссылаются на `InteriorMaterialLibrary`. Поля nullable: старые проекты сохраняют прежние `wallColorArgb` / `floorColorArgb` без принудительной миграции внешнего вида. `RoomSurfaceAppearance` проверяет назначение и атомарно обновляет ID вместе с цветом. Схема JSON 10 сохраняет оба ID. Визуальный слой использует шаблон материала для лёгкой графической фактуры; это 2D/изометрическая иллюстрация, не PBR-рендер.

### Surface appearance extension — ceiling (2026-11-09)

Room finish state now covers wall, floor and ceiling. Ceiling material assignment is represented by `ceilingMaterialId` plus a legacy-safe `ceilingColorArgb`, validated against `SurfaceUse.CEILING`, persisted in schema 11, surfaced in the Materials panel and rendered in the optional ceiling plane in 3D. Design-variant comparison includes ceiling finish changes. Old room JSON without ceiling fields retains the neutral ceiling default.


## Independent wall finishes (schema 12)

Room wall appearance now supports four independent material overrides (`northWallMaterialId`, `eastWallMaterialId`, `southWallMaterialId`, `westWallMaterialId`). A null override inherits the room-wide wall finish and, if that is also absent, the legacy wall color. The Materials panel selects a compass-side wall before applying a catalog finish and can reset it to inherit the room-wide finish. The 3D renderer resolves color and pattern per wall plane; openings remain cut out of the same wall geometry. Validation rejects materials that are not wall-compatible, project persistence stores all four overrides, and design-variant comparison reports side-specific finish changes. Existing projects without these fields retain their previous single-wall-finish appearance.


## Настройки освещения интерьерной сцены (схема проекта 13)

Для каждого помещения хранятся три независимых параметра предпросмотра: интенсивность общего света (0–100%), цветовая температура (2700–6500 K) и контраст затенения граней (0–100%). Параметры проходят проверку проекта, сохраняются в JSON, восстанавливаются с безопасными значениями по умолчанию для старых файлов и учитываются при сравнении вариантов дизайна. Панель «Материалы» позволяет менять их непосредственно в процессе оформления. Отрисовщик применяет параметры к полу, стенам, потолку и мебели; это стилизованная визуализация, а не физически точный расчёт освещения или трассировка лучей.

## Non-blocking room-access diagnostics

`RoomPlanningDiagnostics` analyses the furniture footprint against practical access zones for doors, windows, and wall-mounted engineering markers. Rotated furniture is evaluated as an oriented rectangle using a separating-axis test, not an axis-aligned approximation. Door, window, and engineering clearances are explicit millimetre parameters with safe defaults (900, 300, and 180 mm). Findings are advisory only: they are surfaced in the room workspace but are deliberately not fed into `ProjectValidator`, so a user can keep and save an intentional layout. Regression coverage is in `RoomPlanningDiagnosticsSmoke.kt`.

## Unified room readiness report

`RoomReadinessAnalyzer` composes the existing room-geometry validator, furniture geometry validator, non-blocking access diagnostics, and calibrated multi-photo wall coverage analyzer. It produces stable, categorized findings with severity and practical next actions. The report is advisory only: it does not mutate the project or prevent editing, saving, or export. The landscape workspace surfaces a short prioritized action list so photo capture, calibration, room geometry, furniture placement, and access checks can be reviewed together.

## Предварительный перенос фоторазметки

`PhotoWallTransfer.preview` выполняет неразрушающую оценку переноса отметок всех фотографий комнаты. Для расчёта используется тот же `transferAll`, что и при реальном импорте, но возвращаются только счётчики: готовые и пропущенные фото, создаваемые проёмы и инженерные точки, отметки, которые конвертер не принял, а также группировка причин пропуска. Исходный `ProjectModel` остаётся неизменным. `RoomReadinessAnalyzer` включает эти сведения в общий отчёт, а рабочая область показывает сводку рядом с геометрическими и фото-предупреждениями. Предпросмотр не выполняет автоматическую перспективную коррекцию и не заменяет ручную калибровку стены.


### Фото стен: единый координатный контракт

`PhotoWallCoordinateMapper` является общей точкой преобразования между пикселями ручной фоторазметки и миллиметровой координатой стены. Анализ покрытия и импорт объектов вызывают один mapper, а не повторяют собственные формулы. Стороны NORTH/EAST используют прямое направление, SOUTH/WEST — обратное; `wallOriginXPx` и `floorLineYPx` задают калибровочные якоря для обрезанных кадров. Проекция не исправляет перспективу и не подменяет ручную калибровку.

### Фоторазметка и происхождение геометрии

Проёмы и инженерные точки, созданные переносом с фотографии, сохраняют `sourcePhotoId` и `sourceAnnotationId`. `PhotoWallTransfer.synchronize` повторно проецирует актуальные прямоугольники только на связанные с ними элементы. Удалённая отметка отсоединяет, но не удаляет ранее созданный элемент; несовместимый тип или невалидная геометрия остаются конфликтом для ручного решения. Эти поля входят в схему хранения 14 и необязательны для старых проектов.

## Единая геометрия плана и развёрток стен

`RoomDrawingProjection` формирует `RoomDrawingSet` из одной `RoomModel` и списка мебели помещения. План содержит точный прямоугольник комнаты, проёмы с привязкой к стороне и отметки инженерии; мебель проецируется четырьмя вершинами после поворота вокруг её центра, как в 3D-сцене. Для каждой стороны формируется развёртка: реальные прямоугольники проёмов, остаточные панели стены, инженерные отметки в координатах «вдоль стены / высота» и проекция мебели, расположенной у этой стены. Горизонтальная размерная цепочка строится последовательными отрезками от угла и закрывается до полной длины стены. Панель «Размеры и чертежи» позволяет переключать четыре развёртки и просматривать размеры в миллиметрах. Модуль является read-only проекцией исходной геометрии; он не создаёт производственные карты, раскрой или технологические спецификации.

### Выбор объектов на схематичных проекциях

`RoomDrawingSelection` выбирает мебель по точке в координатах плана или развёртки стены. При пересечении областей приоритет получает более компактный объект, что облегчает выбор мелких предметов. `RoomDrawingPreview` переводит экранные координаты в миллиметры и передаёт ID выбранного предмета в общий редактор; `MainActivity` синхронизирует текущий выбор и поля размеров/позиции с 3D-сценой. Полноформатный пакет A3 не входит в интерфейс и удалён из активной сборки: проекции предназначены для наглядного планирования, а не для производственной документации.


## Schematic selection across room elements

The compact plan/elevation preview uses the same room-space projections as the rest of the editor. Furniture, door/window openings and engineering points are hit-tested in millimetres with screen-scaled tolerances. Selecting an opening or engineering point resolves its stable ID against the active room model, highlights the same element in the preview and opens its existing parameter editor. No duplicate drawing-only entities are created. This is an interior-planning schematic, not a manufacturing drawing workflow.

### Перемещение проёмов и инженерных отметок в общей модели

`ProjectEditor.moveOpening` изменяет только смещение проёма вдоль его стены и повторно запускает проверку геометрии помещения, включая пересечения. `moveEngineeringElement` поддерживает свободные координаты X/Y/Z и автоматически фиксирует настенные элементы на соответствующей плоскости стены. `moveEngineeringOnWall` предоставляет координатную форму для развёртки: расстояние от начала стены плюс высота. Оба инженерных пути ограничивают значения габаритами помещения и поддерживают шаг привязки. Фото provenance сохраняется за счёт копирования исходного объекта. Все виды обновляются из единого `RoomModel`; отдельной редактируемой геометрии схемы нет.

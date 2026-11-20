# Weather Add-on for WebCTRL

Аддон для WebCTRL (Automated Logic / Carrier): периодически загружает текущую погоду и прогноз
с [WeatherAPI.com](https://www.weatherapi.com/) и записывает значения в точки GEO-дерева WebCTRL
(`wfN_*` — на 12 часов вперёд, `wdN_*` — по дням).

| | |
|---|---|
| Версия | 1.1.6 |
| Java | 11 (major 55) |
| Контейнер | Tomcat 9, Servlet 2.5 (`metadata-complete="true"`) |
| API WebCTRL | `directaccess-api-addon-1.7.0.jar` |

## Возможности

- несколько локаций (по умолчанию 21), список сохраняется в `conf/weather-locations.json` и переживает рестарт;
- прогноз на 12 часов и на 3 дня (столько отдаёт бесплатный тариф WeatherAPI);
- запись всех локаций **одной** транзакцией `runWriteAction`, с проверкой `isWritable()`;
- ретраи при сетевых ошибках (3 попытки), ошибка по каждой локации видна в интерфейсе;
- страницы: `/weather` (карточка + графики), `/view` (компактная), `/config` (настройки), `/writeweather` (статус записи).

## Установка

1. Взять `weather.addon` из раздела Releases (или собрать самостоятельно, см. ниже).
2. WebCTRL → System Settings → Add-ons → установить `weather.addon` → перезапустить аддон.
3. Открыть `http://<сервер>:<порт>/weather/`, затем **Настройки** и вставить API-ключ.

## API-ключ

Ключ **не входит в аддон** и не хранится в этом репозитории. Он вводится в `/config`
и сохраняется на сервере в `${catalina.base}/conf/weather-settings.properties`.

- на странице настроек сохранённый ключ показывается только в скрытом виде (`••••••ABCD`);
- пустое поле при сохранении = ключ не меняется, ввод нового значения = замена;
- бесплатный ключ выдаётся на [weatherapi.com](https://www.weatherapi.com/) (Account → API Keys), прогноз — 3 дня.

## Сборка

Проект NetBeans (Ant). Сборка в IDE: *Clean and Build* → `dist/weather_1.1.6.war` + `dist/weather.addon`
(цель `-post-dist` в `build.xml` копирует war в `weather.addon`).

Вручную:

```bash
javac --release 11 -encoding UTF-8 \
      -cp "directaccess-api-addon-1.7.0.jar:json-20251224.jar:<tomcat>/lib/servlet-api.jar" \
      -d build/web/webapp/WEB-INF/classes $(find src/java -name '*.java')
# затем упаковать info.xml + META-INF/ + webapp/ в zip с именем weather.addon
```

Структура архива: `info.xml`, `META-INF/context.xml`, `webapp/` (docbase), внутри —
`WEB-INF/classes`, `WEB-INF/lib/json-*.jar`, `WEB-INF/web.xml`, `js/chart.umd.min.js`.

## Точки WebCTRL

| Точка | Значение |
|---|---|
| `wf0..wf11_highesttemperature` | температура, °C |
| `wf0..wf11_avghumidity` | влажность, % |
| `wf0..wf11_windspeed` | скорость ветра, м/с |
| `wf0..wf11_winddirection` | направление, ° |
| `wd0..wd2_highesttemperature` / `wd0..wd2_mintemperature` | max / min на день, °C |

`N` в `wfN`/`wdN` — смещение часов/дней от текущего момента.

## Ограничения

- бесплатный тариф WeatherAPI отдаёт 3 дня прогноза, поэтому точки `wd3..wd6` не обновляются;
- списки провизорных (primitive) объявлений WebCTRL не синхронизированы с набором записываемых точек;
- учётные данные и keystore подписи в репозиторий не входят (см. `.gitignore`).

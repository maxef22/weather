# Weather Add-on for WebCTRL

Аддон для WebCTRL (Automated Logic / Carrier): периодически загружает текущую погоду и прогноз
с [WeatherAPI.com](https://www.weatherapi.com/) и записывает значения в точки GEO-дерева WebCTRL
(`wfN_*` — на 12 часов вперёд, `wdN_*` — по дням).

| | |
|---|---|
| Версия | 1.2.0 |
| Java | 11 (major 55) |
| Контейнер | Tomcat 9, Servlet 2.5 (`metadata-complete="true"`) |
| API WebCTRL | `directaccess-api-addon-1.7.0.jar` |

## Возможности

- несколько локаций (по умолчанию 21), список сохраняется в `conf/weather-locations.json` и переживает рестарт;
- прогноз на 12 часов и на 3 дня (столько отдаёт бесплатный тариф WeatherAPI);
- психрометрия по каждому часу: энтальпия, точка росы, влагосодержание (по прогнозным T, RH и давлению);
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

Под **каждую локацию** в GEO-дереве создаётся отдельная программа: `#weather_prog_1`, `#weather_prog_2`, …
(`N` — номер локации; путь программы задаётся при добавлении локации в настройках, по умолчанию `#weather_prog_N`).
Аддон не создаёт точки сам — он пишет значения в уже существующие точки программы.

**Почасовой прогноз — 12 часов вперёд (`wf0` … `wf11`, номер = смещение в часах):**

| Точка | Значение |
|---|---|
| `wfN_highesttemperature` | температура, °C |
| `wfN_avghumidity` | относительная влажность, % |
| `wfN_windspeed` | скорость ветра, м/с |
| `wfN_winddirection` | направление ветра, ° |
| `wfN_enthalpy` | энтальпия влажного воздуха, кДж/кг сух. возд. |
| `wfN_dewpoint` | точка росы, °C |
| `wfN_humidityratio` | влагосодержание, г/кг сух. возд. |

**Прогноз по дням — 3 дня (`wd0` … `wd2`, номер = смещение в днях):**

| Точка | Значение |
|---|---|
| `wdN_highesttemperature` | максимальная температура дня, °C |
| `wdN_mintemperature` | минимальная температура дня, °C |

На одну локацию (одну программу) создаются, например, такие точки:

```
#weather_prog_1/wf0_highesttemperature    #weather_prog_1/wf0_enthalpy
#weather_prog_1/wf0_avghumidity           #weather_prog_1/wf0_dewpoint
#weather_prog_1/wf0_windspeed             #weather_prog_1/wf0_humidityratio
#weather_prog_1/wf0_winddirection         ...
...                                       #weather_prog_1/wf11_enthalpy
#weather_prog_1/wf11_highesttemperature   #weather_prog_1/wf11_dewpoint
#weather_prog_1/wf11_avghumidity          #weather_prog_1/wf11_humidityratio
#weather_prog_1/wf11_windspeed
#weather_prog_1/wf11_winddirection        #weather_prog_1/wd0_highesttemperature
                                          #weather_prog_1/wd0_mintemperature
                                          ... #weather_prog_1/wd2_mintemperature
```

Итого за цикл на одну программу: 12 часов × 7 полей = 84 значения и 3 дня × 2 поля = 6 значений, **90 значений**
(без психрометрии было 54 — добавились `enthalpy`, `dewpoint`, `humidityratio`).
Отсутствующие точки не считаются ошибкой — они пропускаются и попадают в список «Не записаны» на странице `/writeweather`.

### Психрометрия

Энтальпия считается по точке росы и абсолютному давлению часа:

```
Pw = Psat(Td)                          Psat — давление насыщения (Магнус), гПа
W  = 0.621945 · Pw / (P − Pw)          влагосодержание, кг/кг сух. возд.
h  = 1.006·T + W·(2501 + 1.86·T)       энтальпия, кДж/кг сух. возд.
```

- давление `P` берётся из почасового прогноза (`pressure_mb`), при отсутствии — текущее давление локации, затем 1013.25 гПа;
- точка росы берётся из прогноза (`dewpoint_c`), при отсутствии рассчитывается из T и RH;
- в WebCTRL `wfN_humidityratio` пишется в **г/кг**, `wfN_enthalpy` — в **кДж/кг**, `wfN_dewpoint` — в **°C**, все с одним знаком после запятой;
- точность ограничена прогнозом (в основном влажностью: ±5 % RH ≈ ±1 кДж/кг), давление влияет слабо (990 вместо 1013 гПа ≈ +0.4 кДж/кг).

Проверочные значения (P = 1013.25 гПа): 0 °C / 80 % → 7.5 кДж/кг; 20 °C / 50 % → 38.5; 25 °C / 60 % → 55.4; 30 °C / 70 % → 78.1.

## Ограничения

- бесплатный тариф WeatherAPI отдаёт 3 дня прогноза, поэтому точки `wd3..wd6` не обновляются;
- списки провизорных (primitive) объявлений WebCTRL не синхронизированы с набором записываемых точек;
- учётные данные и keystore подписи в репозиторий не входят (см. `.gitignore`).

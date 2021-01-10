package ru.nsu.ccfit.zuev.osu.helper;

import android.graphics.PointF;

import org.anddev.andengine.util.Debug;

import java.util.ArrayList;
import java.util.EnumSet;

import ru.nsu.ccfit.zuev.osu.BeatmapData;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.OSUParser;
import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.TrackInfo;
import ru.nsu.ccfit.zuev.osu.game.GameHelper;
import ru.nsu.ccfit.zuev.osu.game.mods.GameMod;
import ru.nsu.ccfit.zuev.osu.menu.ModMenu;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.osuplus.R;
import test.tpdifficulty.TimingPoint;
import test.tpdifficulty.hitobject.HitCircle;
import test.tpdifficulty.hitobject.HitObject;
import test.tpdifficulty.hitobject.HitObjectType;
import test.tpdifficulty.hitobject.Slider;
import test.tpdifficulty.hitobject.SliderType;
import test.tpdifficulty.hitobject.Spinner;
import test.tpdifficulty.tp.AiModtpDifficulty;

public class DifficultyReCalculator {
    private ArrayList<TimingPoint> timingPoints;
    private ArrayList<HitObject> hitObjects;
    private TimingPoint currentTimingPoint = null;
    private int tpIndex = 0;
    private double total, aim, speed, acc;
    private AiModtpDifficulty tpDifficulty;
    private int single, fast_single, stream, jump, switch_fingering, multi;
    private int stream_longest;
    private float real_time;
    //copy from OSUParser.java
    public boolean init(final TrackInfo track, float speedmulti){
        OSUParser parser = new OSUParser(track.getFilename());
        final BeatmapData data;
        if (parser.openFile()) {
            data = parser.readData();
        } else {
            Debug.e("startGame: cannot open file");
            ToastLogger.showText(
                    StringTable.format(R.string.message_error_open,
                            track.getFilename()), true);
            return false;
        }
        float sliderSpeed = parser.tryParseFloat(data.getData("Difficulty", "SliderMultiplier"), 1.0f);

        // Load timing points
        for (final String tempString : data.getData("TimingPoints")) {
            if (timingPoints == null) {
                timingPoints = new ArrayList<>();
            }
            String[] tmpdata = tempString.split("[,]");
            // Ignoring malformed timing point
            if (tmpdata.length < 2) {
                continue;
            }
            float offset = Float.parseFloat(tmpdata[0]);
            float bpm = Float.parseFloat(tmpdata[1]);
            float speed = 1.0f;
            boolean inherited = bpm < 0;

            // The first timing point should always be uninherited, otherwise
            // the beatmap is invalid
            if (currentTimingPoint == null && inherited) {
                return false;
            }

            if (inherited) {
                speed = -100.0f / bpm;
                bpm = currentTimingPoint.getBpm();
            } else {
                bpm = 60000.0f / bpm;
            }
            TimingPoint timing = new TimingPoint(bpm, offset, speed);
            if (!inherited) {
                currentTimingPoint = timing;
            }
            timingPoints.add(timing);
        }
        if (GlobalManager.getInstance().getSongMenu().getSelectedTrack() != track){
            return false;
        }
        final ArrayList<String> hitObjects = data.getData("HitObjects");
        if (hitObjects.size() <= 0) {
            return false;
        }
        for (final String tempString : hitObjects) {
            if (this.hitObjects == null) {
                this.hitObjects = new ArrayList<>();
                tpIndex = 0;
                currentTimingPoint = timingPoints.get(tpIndex);
            }
            String[] data1 = tempString.split("[,]");
            String[] rawdata;
            //Ignoring v10 features
            int dataSize = data1.length;
            while (dataSize > 0 && data1[dataSize - 1].matches("([0-9][:][0-9][|]?)+")) {
                dataSize--;
            }
            if (dataSize < data1.length) {
                rawdata = new String[dataSize];
                for (int i = 0; i < rawdata.length; i++) {
                    rawdata[i] = data1[i];
                }
            } else
                rawdata = data1;

            // Ignoring malformed hitobject
            if (rawdata.length < 4) {
                continue;
            }

            int time = Integer.parseInt(rawdata[2]);
            while (tpIndex < timingPoints.size() - 1 && timingPoints.get(tpIndex + 1).getOffset() <= time) {
                tpIndex++;
            }
            currentTimingPoint = timingPoints.get(tpIndex);
            HitObjectType hitObjectType = HitObjectType.valueOf(Integer.parseInt(rawdata[3]) % 16);
            PointF pos = new PointF(Float.parseFloat(rawdata[0]), Float.parseFloat(rawdata[1]));
            HitObject object = null;
            if (hitObjectType == null) {
                System.out.println(tempString);
                continue;
            }
            if (hitObjectType == HitObjectType.Normal || hitObjectType == HitObjectType.NormalNewCombo) { // hitcircle
                object = new HitCircle((int)(time / speedmulti), pos, currentTimingPoint);
            } else if (hitObjectType == HitObjectType.Spinner) { // spinner
                int endTime = Integer.parseInt(rawdata[5]);
                object = new Spinner((int)(time / speedmulti), (int)(endTime / speedmulti), pos, currentTimingPoint);
            } else if (hitObjectType == HitObjectType.Slider || hitObjectType == HitObjectType.SliderNewCombo) { // slider
                // Ignoring malformed slider
                if (rawdata.length < 8) {
                    continue;
                }
                String[] data2 = rawdata[5].split("[|]");
                SliderType sliderType = SliderType.parse(data2[0].charAt(0));
                ArrayList<PointF> curvePoints = new ArrayList<>();
                for (int i = 1; i < data2.length; i++) {
                    String[] temp = data2[i].split("[:]");
                    curvePoints.add(new PointF(Float.parseFloat(temp[0]), Float.parseFloat(temp[1])));
                }
                int repeat = Integer.parseInt(rawdata[6]);
                float rawLength = Float.parseFloat(rawdata[7]);
                int endTime = time + (int) (rawLength * (600 / currentTimingPoint.getBpm()) / sliderSpeed) * repeat;
                object = new Slider((int)(time / speedmulti), (int)(endTime / speedmulti), pos, currentTimingPoint, sliderType, repeat, curvePoints, rawLength);
            }
            this.hitObjects.add(object);
        }
        return true;
    }
    public float reCalculateStar(final TrackInfo track, float speedmulti, float cs){
        if (!init(track, speedmulti)) {
            return 0f;
        }
        if (GlobalManager.getInstance().getSongMenu().getSelectedTrack() != track){
            return 0f;
        }
        try {
            tpDifficulty = new AiModtpDifficulty();
            tpDifficulty.CalculateAll(hitObjects, cs);
            double star = tpDifficulty.getStarRating();
            if (!timingPoints.isEmpty()){
                timingPoints.clear();
                timingPoints = null;
            }
            if (!hitObjects.isEmpty()){
                hitObjects.clear();
                hitObjects = null;
            }
            if (GlobalManager.getInstance().getSongMenu().getSelectedTrack() != track){
                return 0f;
            }
            return GameHelper.Round(star, 2);
        } catch (Exception e) {
            return 0f;
        }
    }
    //must use reCalculateStar() before this
    public void calculaterPP(final StatisticV2 stat, final TrackInfo track){
        pp(tpDifficulty, track, stat, stat.getAccuracy());
    }
    //must use reCalculateStar() before this
    public void calculaterMaxPP(final StatisticV2 stat, final TrackInfo track){
        pp(tpDifficulty, track, stat, 1f);
    }
    //copy from koohii.java
    private double pp_base(double stars)
    {
        return Math.pow(5.0 * Math.max(1.0, stars / 0.0675) - 4.0, 3.0)
            / 100000.0;
    }
    //copy from koohii.java
    private void pp(AiModtpDifficulty tpDifficulty, TrackInfo track,
                        StatisticV2 stat,
                        float accuracy){
        /* global values --------------------------------------- */
        EnumSet<GameMod> mods = stat.getMod();
        int max_combo = stat.getMaxCombo();
        int combo = track.getMaxCombo();
        int ncircles = track.getHitCircleCount();
        int nobjects = track.getTotalHitObjectCount();
        int nmiss = stat.getMisses();
        float base_ar = getAR(stat, track);
        float base_od = getOD(stat, track);
        if (accuracy == 1f) {
            combo = max_combo;
            nmiss = 0;
        }
        double nobjects_over_2k = nobjects / 2000.0;

        double length_bonus = 0.95 + 0.4 *
            Math.min(1.0, nobjects_over_2k);

        if (nobjects > 2000) {
            length_bonus += Math.log10(nobjects_over_2k) * 0.5;
        }

        double combo_break = Math.min(1.0, Math.pow((double) combo / max_combo, 0.8));

        /* ar bonus -------------------------------------------- */
        double ar_bonus = 0.0;

        if (base_ar > 10.33) {
            ar_bonus += 0.4 * (base_ar - 10.33);
        }

        else if (base_ar < 8.0) {
            ar_bonus +=  0.1 * (8.0 - base_ar);
        }

        ar_bonus = 1 + Math.min(ar_bonus, ar_bonus * nobjects / 1000);
        
        /* aim pp ---------------------------------------------- */
        aim = pp_base(tpDifficulty.getAimStars());
        aim *= length_bonus;
        aim *= combo_break;
        aim *= ar_bonus;

        // aim miss penalty
        if (nmiss > 0){
            aim *= 0.97 * Math.pow(1 - Math.pow((double) nmiss / nobjects, 0.775), nmiss);
        }

        double hd_bonus = 1.0;
        if (mods.contains(GameMod.MOD_HIDDEN)) {
            hd_bonus *= 1.0 + 0.04 * (12.0 - base_ar);
        }
        aim *= hd_bonus;

        if (mods.contains(GameMod.MOD_FLASHLIGHT)) {
            double fl_bonus = 1.0 + 0.35 * Math.min(1.0, nobjects / 200.0);
            if (nobjects > 200) {
                fl_bonus += 0.3 * Math.min(1.0, (nobjects - 200) / 300.0);
            }
            if (nobjects > 500) {
                fl_bonus += (nobjects - 500) / 1200.0;
            }
            aim *= fl_bonus;
        }

        double acc_bonus = 0.5 + accuracy / 2.0;
        double od_squared = base_od * base_od;
        double od_bonus = 0.98 + od_squared / 2500.0;

        aim *= acc_bonus;
        aim *= od_bonus;
        if (mods.contains(GameMod.MOD_AUTOPILOT)) {
            aim *= 0;
        }
        /* speed pp -------------------------------------------- */
        speed = pp_base(tpDifficulty.getSpeedStars());
        speed *= length_bonus;
        speed *= combo_break;
        if (base_ar > 10.33) {
            speed *= ar_bonus;
        }
        speed *= hd_bonus;

        // speed miss penalty
        if (nmiss > 0){
            speed *= 0.97 * Math.pow(1 - Math.pow((double) nmiss / nobjects, 0.775), Math.pow(nmiss, 0.875));
        }

        // scale the speed value with accuracy and OD
        speed *= (0.95 + Math.pow(base_od, 2) / 750) * Math.pow(accuracy, (14.5 - Math.max(base_od, 8)) / 2);
        // scale the speed value with # of 50s to punish doubletapping
        if (accuracy != 1f) {
            speed *= Math.pow(0.98, Math.max(0, stat.getHit50() - nobjects / 500));
        }
        if (mods.contains(GameMod.MOD_RELAX)) {
            speed *= 0;
        }
        /* acc pp ---------------------------------------------- */
        acc = Math.pow(1.52163, base_od) *
            Math.pow(accuracy, 24.0) * 2.83;

        acc *= Math.min(1.15, Math.pow(ncircles / 1000.0, 0.3));

        if (mods.contains(GameMod.MOD_HIDDEN)) {
            acc *= 1.08;
        }

        if (mods.contains(GameMod.MOD_FLASHLIGHT)) {
            acc *= 1.02;
        }

        if (mods.contains(GameMod.MOD_RELAX)) {
            acc *= 0.1;
        }
        /* total pp -------------------------------------------- */
        double final_multiplier = 1.12;

        if (mods.contains(GameMod.MOD_NOFAIL)){
            final_multiplier *= Math.max(0.9, 1.0 - 0.02 * nmiss);
        }

        //if ((mods & MODS_SO) != 0) {
        //    final_multiplier *= 0.95;
        //}

        total = Math.pow(
            Math.pow(aim, 1.1) + Math.pow(speed, 1.1) +
            Math.pow(acc, 1.1),
            1.0 / 1.1
        ) * final_multiplier;
    }
    public double getTotalPP(){
        return total;
    }
    public double getAimPP(){
        return aim;
    }
    public double getSpdPP(){
        return speed;
    }
    public double getAccPP(){
        return acc;
    }
    private float getAR(final StatisticV2 stat, final TrackInfo track){
        // no need to calculate force AR value
        if (stat.isEnableForceAR()) {
            return stat.getForceAR();
        }
        float ar = track.getApproachRate();
        EnumSet<GameMod> mod = stat.getMod();
        if (mod.contains(GameMod.MOD_EASY)) {
            ar *= 0.5f;
        }
        if (mod.contains(GameMod.MOD_HARDROCK)) {
            ar = Math.min(ar * 1.4f, 10);
        }
        float speed = stat.getSpeed();
        if (mod.contains(GameMod.MOD_REALLYEASY)) {
            if (mod.contains(GameMod.MOD_EASY)){
                ar *= 2f;
                ar -= 0.5f;
            }
            ar -= 0.5f;
            ar -= speed - 1.0f;
        }
        ar = GameHelper.Round(GameHelper.ms2ar(GameHelper.ar2ms(Math.min(13.f, ar)) / speed), 2);
        return ar;
    }
    private float getOD(final StatisticV2 stat, final TrackInfo track){
        float od = track.getOverallDifficulty();
        EnumSet<GameMod> mod = stat.getMod();
        if (mod.contains(GameMod.MOD_EASY)) {
            od *= 0.5f;
        }
        if (mod.contains(GameMod.MOD_HARDROCK)) {
            od *= 1.4f;
        }
        float speed = stat.getSpeed();
        if (mod.contains(GameMod.MOD_REALLYEASY)) {
            od *= 0.5f;
        }
        od = Math.min(10.f, od);
        od = GameHelper.Round(GameHelper.ms2od(GameHelper.od2ms(od) / speed), 2);
        return od;
    }
    public float getCS(EnumSet<GameMod> mod, final TrackInfo track){
        float cs = track.getCircleSize();
        if (mod.contains(GameMod.MOD_EASY)) {
            cs -= 1f;
        }
        if (mod.contains(GameMod.MOD_HARDROCK)) {
            cs += 1f;
        }
        if (mod.contains(GameMod.MOD_REALLYEASY)) {
            cs -= 1f;
        }
        if (mod.contains(GameMod.MOD_SMALLCIRCLE)) {
            cs += 4f;
        }
        return cs;
    }
    public float getCS(final StatisticV2 stat, final TrackInfo track){
        return getCS(stat.getMod(), track);
    }
    public float getCS(final TrackInfo track){
        return getCS(ModMenu.getInstance().getMod(), track);
    }
    //must use reCalculateStar() before this
    public boolean calculateMapInfo(final TrackInfo track, float speedmulti, float cs){
        //计算谱面信息
        /*
        120bpm: 125ms(dt1), 140bpm: 107ms(dt3), 80bpm: 187.5(dt4), 180bpm: 83.33ms(dt2)
        单点:低于120bpm，间距小于180
        高速单点:高于120bpm且间距大于90、低于180bpm且间距大于90、小于180
        连打:高于120bpm且间距小于90，高于180bpm且间距大于90、小于180
        跳:间距大于180
        */
        if (init(track, speedmulti) == false) {
            return false;
        }
        if (GlobalManager.getInstance().getSongMenu().getSelectedTrack() != track){
            return false;
        }
        final int ds1 = 90, ds2 = 180;
        final int dt1 = 125, dt2 = 83, dt3 = 107, dt4 = 188;
        single = fast_single = stream = jump = switch_fingering = multi = 0;
        stream_longest = 0;
        int combo = 0;
        int last_delta_time = 0;
        HitObject prev = null;
        boolean first = true;
        int firstObjectTime = 0;
        for (HitObject object : hitObjects){
            if (object.getType() == HitObjectType.Spinner){
                continue;
            }
            if (prev != null){
                int delta_time = object.getStartTime() - prev.getStartTime();
                int distance = (int)Math.sqrt(Math.pow(object.getPos().x - prev.getPos().x, 2) + Math.pow(object.getPos().y - prev.getPos().y, 2));
                if ((delta_time >= dt1 && distance <= ds2) || (delta_time >= dt4 * 4)){
                    single++;
                }
                else if(delta_time >= dt2 && distance >= ds1 && distance <= ds2){
                    fast_single++;
                }
                else if((delta_time <= dt1 && distance <= ds1) || (delta_time <= dt2 && distance <= ds2)){
                    stream++;
                }
                else if(distance >= ds2){
                    jump++;
                }
                else{
                    single++;
                }
                //多押
                if (first && delta_time == 0){
                    first = false;
                    multi += 2;
                }
                else if (delta_time == 0){
                    multi++;
                }
                else if (delta_time != 0){
                    first = true;
                }
                //切指
                if (delta_time < last_delta_time * 1.2f && delta_time > last_delta_time * 0.8f){
                }
                else if ((delta_time < dt3 || last_delta_time < dt3) && last_delta_time != 0 && delta_time < dt4 && last_delta_time < dt4){
                    switch_fingering += 2;
                }
                //最长连打
                if (delta_time > dt3 || last_delta_time > dt3){
                    if (combo != 0 && stream_longest < combo + 2){
                        stream_longest = combo + 2;
                    }
                    combo = 0;
                }
                else{
                    combo++;
                }
                last_delta_time = delta_time; 
            }
            else {
                firstObjectTime = object.getStartTime();
            }
            prev = object;
        }
        //实际游玩时间
        real_time = (hitObjects.get(hitObjects.size() - 1).getEndTime() - firstObjectTime) / 1000f;
        return true;
    }
    public int getSingleCount(){
        return single;
    }
    public int getFastSingleCount(){
        return fast_single;
    }
    public int getStreamCount(){
        return stream;
    }
    public int getJumpCount(){
        return jump;
    }
    public int getSwitchFingeringCount(){
        return switch_fingering;
    }
    public int getMultiCount(){
        return multi;
    }
    public int getLongestStreamCount(){
        return stream_longest;
    }
    public float getRealTime(){
        return real_time;
    }
}
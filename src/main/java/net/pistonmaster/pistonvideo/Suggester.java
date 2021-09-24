package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import net.pistonmaster.pistonvideo.templates.Video;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;

public class Suggester {
    public String suggestions(Request request, Response response) {
        List<Video> videos = new ArrayList<>();

        videos.add(PistonVideoApplication.NYAN_CAT);
        videos.add(PistonVideoApplication.NYAN_CAT);
        videos.add(PistonVideoApplication.NYAN_CAT);
        videos.add(PistonVideoApplication.NYAN_CAT);
        videos.add(PistonVideoApplication.NYAN_CAT);
        videos.add(PistonVideoApplication.NYAN_CAT);

        return new Gson().toJson(videos);
    }
}

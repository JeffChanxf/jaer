/*
 * Copyright (C) 2019 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.minliu;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.ArrayList;
import java.util.Arrays;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * To show corners points from FPGA EFAST corner point detector
 *
 * @author Tobi/Min
 */
@Description("<html>This is the a viwer for demo FPGA eFAST as demonstrated in CVPR2019 EventVision Workshop. The java version of eFAST is also implemented here.<br>"
        + "Liu, M., and Kao, W., and Delbruck, T. (2019). <a href=\"http://openaccess.thecvf.com/content_CVPRW_2019/papers/EventVision/Liu_Live_Demonstration_A_Real-Time_Event-Based_Fast_Corner_Detection_Demo_Based_CVPRW_2019_paper.pdf\">Live Demonstration: A Real-Time Event-Based Fast Corner Detection Demo Based on FPGA</a>.<br>.")
public class HWCornerPointRenderer extends EventFilter2D implements FrameAnnotater {

    private ArrayList<BasicEvent> cornerEvents = new ArrayList(100);
    private double[][][] sae_ = null;
    private static final int INNER_SIZE = 16;
    private static final int OUTER_SIZE = 20;

    private static final int circle3_[][] = {{0, 3}, {1, 3}, {2, 2}, {3, 1},
    {3, 0}, {3, -1}, {2, -2}, {1, -3},
    {0, -3}, {-1, -3}, {-2, -2}, {-3, -1},
    {-3, 0}, {-3, 1}, {-2, 2}, {-1, 3}};
    private static final int circle4_[][] = {{0, 4}, {1, 4}, {2, 3}, {3, 2},
    {4, 1}, {4, 0}, {4, -1}, {3, -2},
    {2, -3}, {1, -4}, {0, -4}, {-1, -4},
    {-2, -3}, {-3, -2}, {-4, -1}, {-4, 0},
    {-4, 1}, {-3, 2}, {-2, 3}, {-1, 4}};

    public HWCornerPointRenderer(AEChip chip) {
        super(chip);
        sae_ = new double[2][500][500];
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        cornerEvents.clear();
        int wrongCornerNum = 0;
        for (BasicEvent e : in) {
            PolarityEvent ein = (PolarityEvent) e;
            int swCornerRet = FastDetectorisFeature(ein) ? 1 : 0;
            int hwCornerRet = (e.getAddress() & 1);
            if (hwCornerRet != swCornerRet) {
                wrongCornerNum++;
            }
            if (swCornerRet == 0) {
//                e.setFilteredOut(true);        
            } else {
                // corner event
                cornerEvents.add(e);
            }
//            if ((hwCornerRet) == 0) {
//                e.setFilteredOut(true);
//            }
//            else
//            {
//                // corner event
//              cornerEvents.add(e);
//            }
        }
//        if (wrongCornerNum != 0)
//        {
//            log.warning(String.format("Detected %d wrong corners.", wrongCornerNum));            
//        }

        return in;
    }

    @Override
    public void resetFilter() {
//        sae_ = new double[2][500][500];
        if (sae_ == null) {
            return;  // on reset maybe chip is not set yet
        }
        for (double[][] b : sae_) {
            for (double[] row : b) {
                Arrays.fill(row, (double) 0);
            }
        }
    }

    @Override
    public void initFilter() {
        sae_ = new double[2][500][500];
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(1, 0, 0);
        for (BasicEvent e : cornerEvents) {
//            if (e.x > chip.getSizeX() || e.y > chip.getSizeY()) {
//                log.warning(e + " is outside array");
//            }
//            DrawGL.drawCircle(gl, e.x, e.y, 2 /*radius*/, 16);
            gl.glPushMatrix();
            DrawGL.drawBox(gl, e.x, e.y, 4,4,0);
//
//            DrawGL.drawLine(gl, e.x - 2, e.y, 4, 0, 2);
//            gl.glPopMatrix();
//            gl.glPushMatrix();
//            DrawGL.drawLine(gl, e.x, e.y - 2, 0, 4, 2);
            gl.glPopMatrix();
        }
    }

    boolean FastDetectorisFeature(PolarityEvent ein) {
        boolean found_streak = false;

        int pix_x = ein.x;
        int pix_y = ein.y;
        int timesmp = ein.timestamp;
        Polarity polarity = ein.polarity;
        if (polarity.equals(Polarity.Off)) {
            found_streak = false;
            return found_streak;
        }

        final int max_scale = 1;
        // only check if not too close to border
        final int cs = max_scale * 20;
        if (pix_x < cs || pix_x >= this.getChip().getSizeX() - cs - 4
                || pix_y < cs || pix_y >= this.getChip().getSizeY() - cs - 4) {
            found_streak = false;
            return found_streak;
        }

        final int pol = 0;

        // update SAE
        sae_[pol][pix_x][pix_y] = timesmp;

        found_streak = false;

        isFeatureOutterLoop:
        for (int i = 0; i < 16; i++) {
            FastDetectorisFeature_label2:
            for (int streak_size = 3; streak_size <= 6; streak_size++) {
                // check that streak event is larger than neighbor
                if ((sae_[pol][pix_x + circle3_[i][0]][pix_y + circle3_[i][1]]) < (sae_[pol][pix_x + circle3_[(i - 1 + 16) % 16][0]][pix_y + circle3_[(i - 1 + 16) % 16][1]])) {
                    continue;
                }

                // check that streak event is larger than neighbor
                if (sae_[pol][pix_x + circle3_[(i + streak_size - 1) % 16][0]][pix_y + circle3_[(i + streak_size - 1) % 16][1]] < sae_[pol][pix_x + circle3_[(i + streak_size) % 16][0]][pix_y + circle3_[(i + streak_size) % 16][1]]) {
                    continue;
                }

                // find the smallest timestamp in corner min_t
                double min_t = sae_[pol][pix_x + circle3_[i][0]][pix_y + circle3_[i][1]];
                FastDetectorisFeature_label1:
                for (int j = 1; j < streak_size; j++) {
                    final double tj = sae_[pol][pix_x + circle3_[(i + j) % 16][0]][pix_y + circle3_[(i + j) % 16][1]];
                    if (tj < min_t) {
                        min_t = tj;
                    }
                }

                //check if corner timestamp is higher than corner
                boolean did_break = false;
                FastDetectorisFeature_label0:
                for (int j = streak_size; j < 16; j++) {
                    final double tj = sae_[pol][pix_x + circle3_[(i + j) % 16][0]][pix_y + circle3_[(i + j) % 16][1]];

                    if (tj >= min_t) {
                        did_break = true;
                        break;
                    }
                }

                if (!did_break) {
                    found_streak = true;
                    break;
                }
            }

            if (found_streak) {
                break;
            }
        }

        if (found_streak) {
            found_streak = false;

            FastDetectorisFeature_label6:
            for (int i = 0; i < 20; i++) {
                FastDetectorisFeature_label5:
                for (int streak_size = 4; streak_size <= 8; streak_size++) {
                    // check that first event is larger than neighbor
                    if (sae_[pol][pix_x + circle4_[i][0]][pix_y + circle4_[i][1]] < sae_[pol][pix_x + circle4_[(i - 1 + 20) % 20][0]][pix_y + circle4_[(i - 1 + 20) % 20][1]]) {
                        continue;
                    }

                    // check that streak event is larger than neighbor
                    if (sae_[pol][pix_x + circle4_[(i + streak_size - 1) % 20][0]][pix_y + circle4_[(i + streak_size - 1) % 20][1]] < sae_[pol][pix_x + circle4_[(i + streak_size) % 20][0]][pix_y + circle4_[(i + streak_size) % 20][1]]) {
                        continue;
                    }

                    double min_t = sae_[pol][pix_x + circle4_[i][0]][pix_y + circle4_[i][1]];
                    FastDetectorisFeature_label4:
                    for (int j = 1; j < streak_size; j++) {
                        final double tj = sae_[pol][pix_x + circle4_[(i + j) % 20][0]][pix_y + circle4_[(i + j) % 20][1]];
                        if (tj < min_t) {
                            min_t = tj;
                        }
                    }

                    boolean did_break = false;
                    FastDetectorisFeature_label3:
                    for (int j = streak_size; j < 20; j++) {
                        final double tj = sae_[pol][pix_x + circle4_[(i + j) % 20][0]][pix_y + circle4_[(i + j) % 20][1]];
                        if (tj >= min_t) {
                            did_break = true;
                            break;
                        }
                    }

                    if (!did_break) {
                        found_streak = true;
                        break;
                    }
                }
                if (found_streak) {
                    break;
                }
            }

        }

        return found_streak;
    }

}

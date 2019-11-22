/* Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Deeply inspired from GVRSphereSceneObject, Copyright 2015 Samsung Electronics Co., LTD,
 * licensed under the Apache License, Version 2.0.
 */

package fr.unice.i3s.uca4svr.toucan_vr.meshes;

import android.util.SparseArray;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRMesh;
import org.gearvrf.utility.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class PartitionedSphereMeshes {

    @SuppressWarnings("unused")
    private static final String TAG = Log.tag(PartitionedSphereMeshes.class);

    private static final int STACK_NUMBER = 18;
    private static final int SLICE_NUMBER = 36;

    private HashMap<int[], GVRMesh> sphereMeshes = new HashMap<>();
    private ArrayList<int[]> meshesIds = new ArrayList<>();

    /**
     * Constructs a sphere scene object with a radius of 1.
     * The sphere is constructed by putting together several meshes.
     *
     * Meshes are defined by the tiles array.
     * The sphere is rendered with at least minSphereStack stacks and minSphereSlice slices.
     * The exact number is computed internally to ease the decomposition of the sphere into
     * several meshes.
     *
     * Make sure that the provided list of tiles actually entirely cover the defined grid and
     * do not overlap or you may have issues when rendering the sphere.
     *
     * The sphere's triangles and normals are facing either in or out.
     * A different texture will be applied to each tile forming the sphere.
     * 
     * @param gvrContext
     *            current {@link GVRContext}
     *
     * @param minSphereStack
     *            The minimum number of stacks the displayed sphere must have.
     *
     * @param minSphereSlice
     *            The minimum number of slices the displayed sphere must have.
     *
     * @param gridStackNumber
     *            The number of stacks in which the sphere is partitioned, used as reference for
     *            tiles coordinates.
     * 
     * @param gridSliceNumber
     *            The number of slices in which the sphere is partitioned, used as a reference for
     *            tiles coordinates.
     *
     * @param tiles
     *            The list of tiles. Each element must contain exactly 4 values:
     *            x, y, w, h; where (x, y) is the upper left corner of the tile
     *            and w and h are the width and height expressed in number of slices and stacks.
     *            x, y, w and h are relative to the gridStackNumber and gridSliceNumber respectively.
     * 
     * @param facingOut
     *            whether the triangles and normals should be facing in or
     *            facing out.
     */
    public PartitionedSphereMeshes(GVRContext gvrContext,
                                   int minSphereStack, int minSphereSlice,
                                   int gridStackNumber, int gridSliceNumber,
                                   ArrayList<int[]> tiles, boolean facingOut) {

        // Do not build a sphere with less than STACK_NUMBER stacks and SLICE_NUMBER slices.
        minSphereStack = minSphereStack < STACK_NUMBER ? STACK_NUMBER : minSphereStack;
        minSphereSlice = minSphereSlice < SLICE_NUMBER ? SLICE_NUMBER : minSphereSlice;

        // Make the number of stack a multiple of gridStackNumber to have a finite number of stacks
        // per tile, and do the same for the slices.
        while(minSphereStack%gridStackNumber != 0) {
            minSphereStack++;
        }

        while(minSphereSlice%gridSliceNumber != 0) {
            minSphereSlice++;
        }

        for(int[] tile : tiles) {
            // Insert the tile at the right position in the ids array.
            int index = 0;
            boolean continues = true;
            while (index < meshesIds.size() && continues) {
                int[] tileAtIndex = meshesIds.get(index);
                if (tile[0] < tileAtIndex[0] ||
                        (tile[0] == tileAtIndex[0] && tile[1] < tileAtIndex[1])) {
                    continues = false;
                }
                if (continues) {
                    index++;
                }
            }
            meshesIds.add(index < 0 ? 0 : index, tile);

            // Build the mesh and add it to the mesh set.
            sphereMeshes.put(tile, createTile(gvrContext, tile, minSphereStack, minSphereSlice,
                    gridStackNumber, gridSliceNumber, facingOut));
        }
    }

    /**
     * Builds the mesh for the provided tile definition and sphere parameters.
     *
     * @param gvrContext
     *      The current context in which the mesh is created.
     * @param tile
     *      The tile representation, 4 ints containing [x, y, w, h] corresponding to the
     *      start position and width and height in the grid referential.
     * @param stackNumber
     *      The number of stacks the full sphere has.
     * @param sliceNumber
     *      The number of slices the full sphere has.
     * @param stacksInGrid
     *      The number of stacks in the grid.
     * @param slicesInGrid
     *      The number of slices in the grid.
     * @param facingOut
     *      If the normals are facing in or out
     * @return
     *      A mesh representing the defined tile.
     */
    private GVRMesh createTile(GVRContext gvrContext, int[] tile, int stackNumber, int sliceNumber,
                               int stacksInGrid, int slicesInGrid, boolean facingOut) {
        int gridUnitSliceNumber = sliceNumber / slicesInGrid;
        int gridUnitStackNumber = stackNumber / stacksInGrid;
        int startingSlice = tile[1] * gridUnitSliceNumber;
        int startingStack = tile[0] * gridUnitStackNumber;
        int tileSliceNumber = tile[3] * gridUnitSliceNumber;
        int tileStackNumber = tile[2] * gridUnitStackNumber;

        float[] vertices;
        float[] normals;
        float[] texCoords;
        char[] indices;

        int vertexCount = 0;
        int texCoordCount = 0;
        char indexCount = 0;
        char triangleCount = 0;

        int vertexNumber = 0;
        int triangleNumber = 0;
        int bodyStacksNumber = tileStackNumber;

        if (startingStack == 0) {
            vertexNumber += 3 * tileSliceNumber;
            triangleNumber += 3 * tileSliceNumber;
            bodyStacksNumber--;
        }

        if (startingStack + stacksInGrid == stackNumber) {
            vertexNumber += 3 * tileSliceNumber;
            triangleNumber += 3 * tileSliceNumber;
            bodyStacksNumber--;
        }

        vertexNumber += 4 * bodyStacksNumber * tileSliceNumber;
        triangleNumber += 6 * bodyStacksNumber * tileSliceNumber;
        vertices = new float[3 * vertexNumber];
        normals = new float[3 * vertexNumber];
        texCoords = new float[2 * vertexNumber];
        indices = new char[triangleNumber];

        for (int stack = startingStack; stack < startingStack + tileStackNumber;
             stack++ ) {

            float stackPercentage0 = ((float) (stack) / stackNumber);
            float stackPercentage1 = ((float) (stack + 1) / stackNumber);

            float t0 = ((float) (stack - startingStack) / tileStackNumber);
            float t1 = ((float)(stack - startingStack + 1) / tileStackNumber);

            if (stack == 0) {
                stackPercentage0 = 1f / stackNumber;
                stackPercentage1 = 0f;
                t0 = 1f / tileStackNumber;
                t1 = stackPercentage1;
            }

            double theta0 = stackPercentage0 * Math.PI;
            double theta1 = stackPercentage1 * Math.PI;
            double cosTheta0 = Math.cos(theta0);
            double sinTheta0 = Math.sin(theta0);
            double cosTheta1 = Math.cos(theta1);
            double sinTheta1 = Math.sin(theta1);

            if (stack == 0 || stack == stackNumber - 1) {
                // Computing slices from the top or bottom cap that belong to the current tile.
                boolean top = stack == 0;
                for (int slice = startingSlice; slice < startingSlice + tileSliceNumber; slice++) {
                    float slicePercentage0 = ((float) (slice) / sliceNumber);
                    float slicePercentage1 = ((float) (slice + 1) / sliceNumber);
                    double phi0 = slicePercentage0 * 2.0 * Math.PI;
                    double phi1 = slicePercentage1 * 2.0 * Math.PI;
                    float s0, s1;
                    if (facingOut) {
                        s0 = 1.0f - ((float) (slice - startingSlice) / tileSliceNumber);
                        s1 = 1.0f - ((float) (slice - startingSlice + 1) / tileSliceNumber);
                    } else {
                        s0 = ((float) (slice - startingSlice) / tileSliceNumber);
                        s1 = ((float) (slice - startingSlice + 1) / tileSliceNumber);
                    }
                    float s2 = (s0 + s1) / 2.0f;
                    double cosPhi0 = Math.cos(phi0);
                    double sinPhi0 = Math.sin(phi0);
                    double cosPhi1 = Math.cos(phi1);
                    double sinPhi1 = Math.sin(phi1);

                    float x0 = (float) (sinTheta0 * cosPhi0);
                    float y0 = (float) cosTheta0;
                    float z0 = (float) (sinTheta0 * sinPhi0);

                    float x1 = (float) (sinTheta0 * cosPhi1);
                    float y1 = (float) cosTheta0;
                    float z1 = (float) (sinTheta0 * sinPhi1);

                    float x2 = (float) (sinTheta1 * cosPhi0);
                    float y2 = (float) cosTheta1;
                    float z2 = (float) (sinTheta1 * sinPhi0);

                    vertices[vertexCount + 0] = x0;
                    vertices[vertexCount + 1] = y0;
                    vertices[vertexCount + 2] = z0;

                    vertices[vertexCount + 3] = x1;
                    vertices[vertexCount + 4] = y1;
                    vertices[vertexCount + 5] = z1;

                    vertices[vertexCount + 6] = x2;
                    vertices[vertexCount + 7] = y2;
                    vertices[vertexCount + 8] = z2;

                    if (facingOut) {
                        normals[vertexCount + 0] = x0;
                        normals[vertexCount + 1] = y0;
                        normals[vertexCount + 2] = z0;

                        normals[vertexCount + 3] = x1;
                        normals[vertexCount + 4] = y1;
                        normals[vertexCount + 5] = z1;

                        normals[vertexCount + 6] = x2;
                        normals[vertexCount + 7] = y2;
                        normals[vertexCount + 8] = z2;
                    } else {
                        normals[vertexCount + 0] = -x0;
                        normals[vertexCount + 1] = -y0;
                        normals[vertexCount + 2] = -z0;

                        normals[vertexCount + 3] = -x1;
                        normals[vertexCount + 4] = -y1;
                        normals[vertexCount + 5] = -z1;

                        normals[vertexCount + 6] = -x2;
                        normals[vertexCount + 7] = -y2;
                        normals[vertexCount + 8] = -z2;
                    }

                    texCoords[texCoordCount + 0] = s0;
                    texCoords[texCoordCount + 1] = t0;
                    texCoords[texCoordCount + 2] = s1;
                    texCoords[texCoordCount + 3] = t0;
                    texCoords[texCoordCount + 4] = s2;
                    texCoords[texCoordCount + 5] = t1;

                    if ((facingOut && top) || (!facingOut && !top)) {
                        indices[indexCount + 0] = (char) (triangleCount + 1);
                        indices[indexCount + 1] = (char) (triangleCount + 0);
                        indices[indexCount + 2] = (char) (triangleCount + 2);
                    } else {
                        indices[indexCount + 0] = (char) (triangleCount + 0);
                        indices[indexCount + 1] = (char) (triangleCount + 1);
                        indices[indexCount + 2] = (char) (triangleCount + 2);
                    }

                    vertexCount += 9;
                    texCoordCount += 6;
                    indexCount += 3;
                    triangleCount += 3;
                }
            } else {
                // Computing slices from the sphere body that belong to the current tile.
                for (int slice = startingSlice; slice < startingSlice + tileSliceNumber; slice++) {
                    float slicePercentage0 = ((float) (slice) / sliceNumber);
                    float slicePercentage1 = ((float) (slice + 1) / sliceNumber);
                    double phi0 = slicePercentage0 * 2.0 * Math.PI;
                    double phi1 = slicePercentage1 * 2.0 * Math.PI;
                    float s0, s1;
                    if (facingOut) {
                        s0 = 1.0f - ((float) (slice - startingSlice) / tileSliceNumber);
                        s1 = 1.0f - ((float) (slice - startingSlice + 1) / tileSliceNumber);
                    } else {
                        s0 = ((float) (slice - startingSlice) / tileSliceNumber);
                        s1 = ((float) (slice - startingSlice + 1) / tileSliceNumber);
                    }
                    double cosPhi0 = Math.cos(phi0);
                    double sinPhi0 = Math.sin(phi0);
                    double cosPhi1 = Math.cos(phi1);
                    double sinPhi1 = Math.sin(phi1);

                    float x0 = (float) (sinTheta0 * cosPhi0);
                    float y0 = (float) cosTheta0;
                    float z0 = (float) (sinTheta0 * sinPhi0);

                    float x1 = (float) (sinTheta0 * cosPhi1);
                    float y1 = (float) cosTheta0;
                    float z1 = (float) (sinTheta0 * sinPhi1);

                    float x2 = (float) (sinTheta1 * cosPhi0);
                    float y2 = (float) cosTheta1;
                    float z2 = (float) (sinTheta1 * sinPhi0);

                    float x3 = (float) (sinTheta1 * cosPhi1);
                    float y3 = (float) cosTheta1;
                    float z3 = (float) (sinTheta1 * sinPhi1);

                    vertices[vertexCount + 0] = x0;
                    vertices[vertexCount + 1] = y0;
                    vertices[vertexCount + 2] = z0;

                    vertices[vertexCount + 3] = x1;
                    vertices[vertexCount + 4] = y1;
                    vertices[vertexCount + 5] = z1;

                    vertices[vertexCount + 6] = x2;
                    vertices[vertexCount + 7] = y2;
                    vertices[vertexCount + 8] = z2;

                    vertices[vertexCount + 9] = x3;
                    vertices[vertexCount + 10] = y3;
                    vertices[vertexCount + 11] = z3;

                    if (facingOut) {
                        normals[vertexCount + 0] = x0;
                        normals[vertexCount + 1] = y0;
                        normals[vertexCount + 2] = z0;

                        normals[vertexCount + 3] = x1;
                        normals[vertexCount + 4] = y1;
                        normals[vertexCount + 5] = z1;

                        normals[vertexCount + 6] = x2;
                        normals[vertexCount + 7] = y2;
                        normals[vertexCount + 8] = z2;

                        normals[vertexCount + 9] = x3;
                        normals[vertexCount + 10] = y3;
                        normals[vertexCount + 11] = z3;
                    } else {
                        normals[vertexCount + 0] = -x0;
                        normals[vertexCount + 1] = -y0;
                        normals[vertexCount + 2] = -z0;

                        normals[vertexCount + 3] = -x1;
                        normals[vertexCount + 4] = -y1;
                        normals[vertexCount + 5] = -z1;

                        normals[vertexCount + 6] = -x2;
                        normals[vertexCount + 7] = -y2;
                        normals[vertexCount + 8] = -z2;

                        normals[vertexCount + 9] = -x3;
                        normals[vertexCount + 10] = -y3;
                        normals[vertexCount + 11] = -z3;
                    }

                    texCoords[texCoordCount + 0] = s0;
                    texCoords[texCoordCount + 1] = t0;
                    texCoords[texCoordCount + 2] = s1;
                    texCoords[texCoordCount + 3] = t0;
                    texCoords[texCoordCount + 4] = s0;
                    texCoords[texCoordCount + 5] = t1;
                    texCoords[texCoordCount + 6] = s1;
                    texCoords[texCoordCount + 7] = t1;

                    // one quad looking from outside toward center
                    //
                    // @formatter:off
                    //
                    //     s1 --> s0
                    //
                    // t0   1-----0
                    //  |   |     |
                    //  v   |     |
                    // t1   3-----2
                    //
                    // @formatter:on
                    //
                    // Note that tex_coord t increase from top to bottom because the
                    // texture image is loaded upside down.
                    if (facingOut) {
                        indices[indexCount + 0] = (char) (triangleCount + 0);
                        indices[indexCount + 1] = (char) (triangleCount + 1);
                        indices[indexCount + 2] = (char) (triangleCount + 2);

                        indices[indexCount + 3] = (char) (triangleCount + 2);
                        indices[indexCount + 4] = (char) (triangleCount + 1);
                        indices[indexCount + 5] = (char) (triangleCount + 3);
                    } else {
                        indices[indexCount + 0] = (char) (triangleCount + 0);
                        indices[indexCount + 1] = (char) (triangleCount + 2);
                        indices[indexCount + 2] = (char) (triangleCount + 1);

                        indices[indexCount + 3] = (char) (triangleCount + 2);
                        indices[indexCount + 4] = (char) (triangleCount + 3);
                        indices[indexCount + 5] = (char) (triangleCount + 1);
                    }

                    vertexCount += 12;
                    texCoordCount += 8;
                    indexCount += 6;
                    triangleCount += 4;
                }
            }
        }

        GVRMesh mesh = new GVRMesh(gvrContext);
        mesh.setVertices(vertices);
        mesh.setNormals(normals);
        mesh.setTexCoords(texCoords);
        mesh.setIndices(indices);

        return mesh;
    }

    /**
     * Gives the mesh corresponding to the tile containing the grid element at the
     * given coordinates. Grid coordinates are given relative to the griSliceNumber
     * and gridStackNumber given at initialization.
     *
     * @param x
     *      The grid slice coordinate
     * @param y
     *      The grid stack coordinate
     * @return
     *      The mesh representing the tile containing the grid element at the given coordinates.
     *      Null if no mesh corresponds to the given coordinates.
     */
    public GVRMesh getMeshAt(int x,int y) {
        for (int[] slice : sphereMeshes.keySet()) {
            if (x >= slice[0] && y >= slice[1]
                    && x < slice[0] + slice[2] && y < slice[1] + slice[3]) {
                return sphereMeshes.get(slice);
            }
        }
        return null;
    }

    public int getNumberOfTiles() {
        return this.sphereMeshes.size();
    }

    public GVRMesh getMeshById(int id) {
        if (meshesIds.get(id) != null) {
            return this.sphereMeshes.get(meshesIds.get(id));
        } else {
            return null;
        }
    }
}

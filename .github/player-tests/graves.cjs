// Real vanilla clients exercise production jars; stdout is a JSON console bridge to the runner.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { once } = require('node:events')
const mineflayer = require('mineflayer')

const [port, version, phase, directory] = process.argv.slice(2)
const name = 'FiwQA'
const emit = value => process.stdout.write(JSON.stringify(value) + '\n')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const deadline = setTimeout(() => { emit({ error: 'Player verification timed out' }); process.exit(1) }, 150000)
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name,
  auth: 'offline', version, respawn: false })
bot.on('kicked', reason => { emit({ error: 'Client kicked', reason }); process.exit(1) })
bot.on('error', error => { emit({ error: error.stack }); process.exit(1) })
const messages = []
bot.on('messagestr', text => messages.push(text))

async function until(condition, description, timeout = 12000) {
  const end = Date.now() + timeout
  while (!condition()) {
    if (Date.now() > end) throw new Error(`Timed out: ${description}; chat=${messages.slice(-12).join('; ')}`)
    await delay(100)
  }
}
// mineflayer waits for a 5x5 chunk square with a fixed 10 s limit. A slow server start or a
// spawn on a chunk border must not fail the run once the client's own chunk has arrived.
async function chunksLoaded() {
  for (let attempt = 0; attempt < 6; attempt++) {
    try { return await bot.waitForChunksToLoad() } catch (error) {
      if (bot.world.getColumnAt(bot.entity.position)) return
    }
  }
  throw new Error('Client chunks never loaded')
}
async function command(command) { emit({ console: command }); await delay(250) }
// Builds test terrain only after the server confirms it. Chunks away from the player are not
// loaded (no spawn chunks on newer versions), and an unverified fill fails silently, leaving the
// client inside natural terrain of a random world or a hazard test without its hazard.
async function build(dimension, [x1, z1, x2, z2], fills) {
  const scoped = text => `execute in minecraft:${dimension} run ${text}`
  await command(scoped(`forceload add ${x1} ${z1} ${x2} ${z2}`))
  for (const fill of fills) {
    const end = Date.now() + 20000
    for (;;) {
      const start = messages.length
      bot.chat('/' + scoped(fill))
      const done = () => messages.slice(start).some(m => m.includes('Successfully filled') || m.includes('No blocks were filled'))
      const answered = () => done() || messages.length > start
      for (let waited = 0; !answered() && waited < 2000; waited += 100) await delay(100)
      if (done()) break
      if (Date.now() > end) throw new Error(`Test terrain was not built: ${fill}; ${messages.slice(-5).join('; ')}`)
      await delay(500)
    }
  }
}
async function inDimension(dimension) {
  const marker = `QA_DIM_${dimension}`
  const start = messages.length
  for (let attempt = 0; attempt < 24; attempt++) {
    await command(`execute as ${name} at @s if dimension minecraft:${dimension} run tellraw ${name} {"text":"${marker}"}`)
    if (messages.slice(start).some(m => m.includes(marker))) return
    await delay(250)
  }
  throw new Error(`Server did not confirm dimension ${dimension}; client reported ${bot.game.dimension}`)
}
function allItems() { return bot.inventory.slots.filter(Boolean) }
function count(items, itemName) { return items.filter(item => item.name === itemName).reduce((n, item) => n + item.count, 0) }
function fingerprint(item) {
  // Slot and count change during transfer. Preserve all actual NBT/component data, including enchants.
  const normalize = value => {
    if (typeof value === 'bigint') return value.toString()
    if (Array.isArray(value)) return value.map(normalize)
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(k => [k, normalize(value[k])]))
    return value
  }
  return JSON.stringify(normalize({ name: item.name, nbt: item.nbt ?? null,
    components: item.components ? [...item.components].sort((a, b) => JSON.stringify(normalize(a)).localeCompare(JSON.stringify(normalize(b)))) : null,
    removedComponents: item.removedComponents ? [...item.removedComponents].sort() : null }))
}
async function marker() {
  // Console commands share one ordered server input stream, avoiding a queued client
  // teleport racing a later dimension switch. Keep the actual player command context.
  await command(`execute as ${name} at @s run graves tp`)
  await until(() => Object.values(bot.entities).some(e => e.name === 'armor_stand' && e.position.distanceTo(bot.entity.position) < 7), 'visible grave marker')
  return Object.values(bot.entities).filter(e => e.name === 'armor_stand' && e.position.distanceTo(bot.entity.position) < 7)
    .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0]
}
function interact(entity, sneaking) {
  const params = version.startsWith('26.')
    ? { target: entity.id, hand: 'main_hand', location: { x: 0, y: 0, z: 0 }, sneaking }
    : { target: entity.id, mouse: 0, hand: 0, sneaking }
  bot._client.write('use_entity', params)
}
async function open(entity) {
  const opened = once(bot, 'windowOpen')
  interact(entity, false)
  const [window] = await Promise.race([opened, delay(12000).then(() => { throw new Error('Grave GUI did not open') })])
  await delay(250)
  return window
}
async function assertNoGraves() {
  const start = messages.length
  bot.chat('/graves list')
  await until(() => messages.slice(start).some(m => m.includes('No active graves.')), 'no duplicate active grave')
}

async function main() {
  await once(bot, 'spawn')
  bot.physicsEnabled = false
  await chunksLoaded()
  await command(`op ${name}`)
  await command('difficulty peaceful')
  await build('overworld', [-6, -6, 6, 6], ['fill -6 64 -6 6 64 6 minecraft:stone', 'fill -6 65 -6 6 69 6 minecraft:air'])
  await command(`tp ${name} 0.5 65 0.5`)
  await command(`spawnpoint ${name} 0 65 0`)
  const stateFile = path.join(directory, 'graves-player-state.json')

  if (phase === 'create') {
    await command(`clear ${name}`)
    await command(`experience set ${name} 0 levels`)
    await command(`experience set ${name} 0 points`)
    await command(`item replace entity ${name} weapon.mainhand with minecraft:diamond_sword`)
    await command(`enchant ${name} minecraft:sharpness 5`)
    await command(`give ${name} minecraft:diamond 17`)
    await command(`give ${name} minecraft:diamond_boots 1`)
    await command(`item replace entity ${name} armor.head with minecraft:diamond_helmet`)
    await command(`item replace entity ${name} weapon.offhand with minecraft:shield`)
    await command(`experience add ${name} 315 points`)
    await until(() => count(allItems(), 'diamond') === 17 && bot.experience.level === 15, 'fixture items and XP')
    const sword = allItems().find(i => i.name === 'diamond_sword')
    assert(sword, 'fixture sword exists')
    const enchants = Array.isArray(sword.enchants) ? sword.enchants : sword.enchants.enchantments
    assert(enchants.some(e => (e.lvl ?? e.level) === 5), 'fixture really has the registry-backed enchantment')
    fs.writeFileSync(stateFile, JSON.stringify({ sword: fingerprint(sword), experience: 315 }))
    const died = once(bot, 'death')
    await command(`kill ${name}`)
    await died
    const spawned = once(bot, 'spawn')
    bot.respawn()
    await spawned
    await chunksLoaded()
    assert.equal(count(allItems(), 'diamond'), 0, 'loot removed from dead player')
    assert.equal(count(allItems(), 'diamond_sword'), 0)
    const original = await marker()
    const position = original.position.clone()
    await command(`execute in minecraft:the_nether run tp ${name} ${position.x} ${position.y} ${position.z}`)
    await inDimension('the_nether')
    await delay(1000)
    interact(original, false)
    await delay(1000)
    assert.equal(bot.currentWindow?.title ?? null, null, 'a stale grave entity id cannot open a grave from another dimension')
    assert.equal(bot.experience.level, 0, 'cross-dimension interaction cannot consume grave XP')
    await command(`execute in minecraft:overworld run tp ${name} ${position.x} ${position.y} ${position.z}`)
    await inDimension('overworld')
    await chunksLoaded()
    await delay(1000)
    emit({ assertion: 'death created a visible grave; enchanted items and XP left for restart; cross-dimension access denied' })
  } else if (phase === 'partial') {
    const state = JSON.parse(fs.readFileSync(stateFile))
    const window = await open(await marker())
    const items = window.containerItems()
    assert.equal(count(items, 'diamond'), 17, 'all diamonds survived restart')
    assert.equal(count(items, 'diamond_boots'), 1)
    assert.equal(count(items, 'diamond_helmet'), 1, 'equipped armor was captured')
    assert.equal(count(items, 'shield'), 1, 'offhand item was captured')
    assert.equal(fingerprint(items.find(i => i.name === 'diamond_sword')), state.sword, 'enchanted sword survived serialization')
    await until(() => bot.experience.level === 15, 'XP restored on opening')
    await window.withdraw(bot.registry.itemsByName.diamond.id, null, 5)
    assert.equal(count(window.items(), 'diamond'), 5, 'partial GUI withdrawal delivered exactly five')
    assert.equal(count(window.containerItems(), 'diamond'), 12, 'exactly twelve remain in grave')
    await delay(1000)
    bot.closeWindow(window)
    await delay(1000)
    emit({ assertion: 'GUI withdrawal preserves exact remainder and grants XP once' })
  } else if (phase === 'collect') {
    const state = JSON.parse(fs.readFileSync(stateFile))
    const window = await open(await marker())
    assert.equal(count(window.containerItems(), 'diamond'), 12, 'partial collection persisted across restart')
    assert.equal(fingerprint(window.containerItems().find(i => i.name === 'diamond_sword')), state.sword)
    assert.equal(bot.experience.level, 15, 'reopening did not award XP twice')
    bot.closeWindow(window)
    // Five diamonds were already withdrawn and asserted in the preceding phase.
    // Fill every slot, leaving space for only four of the twelve remaining diamonds.
    await command(`clear ${name}`)
    await command(`give ${name} minecraft:stone 2304`)
    await command(`item replace entity ${name} hotbar.0 with minecraft:diamond 60`)
    await until(() => count(allItems(), 'diamond') === 60, 'full inventory fixture')
    interact(await marker(), true)
    await delay(500)
    assert.equal(count(allItems(), 'diamond'), 64, 'only the available four diamonds merged')
    assert.equal(count(allItems(), 'diamond_boots'), 1, 'boots equipped into empty armor slot')
    await delay(1000)
    const dropped = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 12)
    const ground = dropped.map(e => e.getDroppedItem()).filter(Boolean)
    assert.equal(count(allItems(), 'diamond') + count(ground, 'diamond'), 72, 'every overflow diamond exists before pickup')
    assert.equal(count(allItems(), 'diamond_sword') + count(ground, 'diamond_sword'), 1, 'sword exists in inventory or on ground')
    assert.equal(fingerprint([...allItems(), ...ground].find(i => i.name === 'diamond_sword')), state.sword)
    await command(`clear ${name} minecraft:stone`)
    // Walk the collection path using server teleports, avoiding client physics/terrain assumptions.
    for (const entity of dropped) {
      await command(`tp ${name} ${entity.position.x} ${entity.position.y} ${entity.position.z}`)
      await delay(1500)
    }
    await until(() => count(allItems(), 'diamond') === 72 && count(allItems(), 'diamond_sword') === 1,
      'overflow drops picked up without loot loss', 20000)
    assert.equal(count(allItems(), 'diamond_helmet'), 1)
    await until(() => count(allItems(), 'shield') === 1, 'offhand shield recovered from overflow')
    assert.equal(fingerprint(allItems().find(i => i.name === 'diamond_sword')), state.sword)
    await assertNoGraves()
    emit({ assertion: 'full-inventory quick collect conserved items through partial merge, armor and overflow drops' })
  } else if (phase === 'verify') {
    const state = JSON.parse(fs.readFileSync(stateFile))
    assert.equal(count(allItems(), 'diamond'), 72, 'recovered items persisted across final restart')
    assert.equal(count(allItems(), 'diamond_boots'), 1)
    assert.equal(count(allItems(), 'diamond_helmet'), 1)
    assert.equal(count(allItems(), 'shield'), 1)
    assert.equal(fingerprint(allItems().find(i => i.name === 'diamond_sword')), state.sword)
    assert.equal(bot.experience.level, 15)
    await assertNoGraves()
    emit({ assertion: 'final restart preserved recovered loot and did not resurrect the grave' })
  } else if (phase === 'hazards') {
    // The conserved baseline was checked before replacing this disposable fixture.
    for (const [index, hazard] of ['water', 'lava', 'void'].entries()) {
      await command(`clear ${name}`)
      await command(`experience set ${name} 0 levels`)
      await command(`experience set ${name} 0 points`)
      await command(`give ${name} minecraft:diamond ${31 + index}`)
      await command(`effect give ${name} minecraft:fire_resistance 60 0 true`)
      await command(`effect give ${name} minecraft:water_breathing 60 0 true`)
      if (hazard === 'void') {
        await command(`execute in minecraft:the_end run tp ${name} 2000.5 -10 2000.5`)
        await inDimension('the_end')
        await delay(2500)
      } else {
        await build('overworld', [18, -2, 22, 2], ['fill 18 64 -2 22 64 2 minecraft:stone', `fill 18 65 -2 22 67 2 minecraft:${hazard}`])
        await command(`tp ${name} 20.5 65 0.5`)
      }
      const died = once(bot, 'death')
      await command(`kill ${name}`)
      await died
      const spawned = once(bot, 'spawn')
      bot.respawn()
      await spawned
      await chunksLoaded()
      const entity = await marker()
      const config = JSON.parse(fs.readFileSync(path.join(directory, 'config/fiwgraves/config.json')))
      const graveY = entity.position.y - config.markerHeight
      assert(graveY >= (hazard === 'void' ? 1 : 68), `${hazard} grave was placed above the hazard (y=${graveY})`)
      const window = await open(entity)
      assert.equal(count(window.containerItems(), 'diamond'), 31 + index, `${hazard} death preserved every item`)
      await window.withdraw(bot.registry.itemsByName.diamond.id, null, 31 + index)
      await delay(1000)
      bot.closeWindow(window)
      await delay(1000)
      await assertNoGraves()
      assert.equal(count(allItems(), 'diamond'), 31 + index)
      emit({ assertion: `${hazard} death created a reachable grave with all loot` })
      await command(`execute in minecraft:overworld run tp ${name} 0.5 65 0.5`)
      await inDimension('overworld')
      await delay(2500)
    }
    const keepInventory = (version.startsWith('26.') || version === '1.21.11') ? 'minecraft:keep_inventory' : 'keepInventory'
    await command(`gamerule ${keepInventory} true`)
    const died = once(bot, 'death')
    await command(`kill ${name}`)
    await died
    const spawned = once(bot, 'spawn')
    bot.respawn()
    await spawned
    await chunksLoaded()
    await until(() => count(allItems(), 'diamond') === 33, 'keepInventory retained items on player')
    await assertNoGraves()
    await command(`gamerule ${keepInventory} false`)
    emit({ assertion: 'keepInventory creates no duplicate grave or loot' })
  } else if (phase === 'hazards-restart') {
    assert.equal(count(allItems(), 'diamond'), 33, 'hazard recovery and keepInventory persisted')
    await assertNoGraves()
    emit({ assertion: 'hazard recovery remained conserved after restart' })
  } else throw new Error(`Unknown test phase: ${phase}`)
  const ended = once(bot, 'end')
  bot.quit('Verification complete')
  await ended
  clearTimeout(deadline)
  emit({ passed: phase, version })
}

main().catch(error => { emit({ error: error.stack }); bot.quit(); process.exit(1) })
